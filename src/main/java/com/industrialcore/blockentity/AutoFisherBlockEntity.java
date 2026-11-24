package com.industrialcore.blockentity;

import com.industrialcore.agri.AgriLogic;
import com.industrialcore.block.AutoFisherBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.AutoFisherMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 90: BlockEntity for the Automatic Fisher ({@code auto_fisher}).
 *
 * <p>Requires a water source ({@code Fluids.WATER}) directly below or on any of the 6 adjacent
 * faces. Runs vanilla {@code BuiltInLootTables.FISHING} with optional enchanted Fishing Rod boost
 * (Luck of the Sea + Lure, zero durability loss) and Motor/Battery upgrades.</p>
 */
public class AutoFisherBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "auto_fisher";

    public static final int ROD_SLOT = 0;
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_COUNT = 9;
    public static final int MOTOR_SLOT = 10;
    public static final int BATTERY_SLOT = 11;
    public static final int SLOT_COUNT = 12;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_HAS_WATER = 5;
    public static final int DATA_CAUGHT = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_CAUGHT = "Caught";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final FisherInventory inventory = new FisherInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            AgriLogic.FISHER_BASE_CAPACITY, AgriLogic.FISHER_MAX_RECEIVE);

    private static final Direction[] DIRECTIONS = Direction.values();
    private int progress;
    private int caughtTotal;
    private boolean hasWater;
    private boolean waterChecked;
    private MachineStatus status = MachineStatus.IDLE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> currentDuration();
                case DATA_HAS_WATER -> hasWater ? 1 : 0;
                case DATA_CAUGHT -> caughtTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public AutoFisherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_FISHER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoFisherBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        if (!waterChecked || Math.floorMod(level.getGameTime() + worldPosition.hashCode(), 20L) == 0L) {
            hasWater = detectWater(level);
            waterChecked = true;
        }
        if (!hasWater) {
            status = MachineStatus.IDLE;
            progress = 0;
            updateLit(level, state, false);
            return;
        }

        if (status == MachineStatus.OUTPUT_FULL) {
            if (Math.floorMod(level.getGameTime() + worldPosition.hashCode(), 10L) != 0L || !hasAnyOutputSpace()) {
                return;
            }
        } else if (progress == 0 && !hasAnyOutputSpace()) {
            status = MachineStatus.OUTPUT_FULL;
            updateLit(level, state, false);
            return;
        }

        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int fePerTick = AgriLogic.fisherEnergyPerTick(getBatteryCount(), ownerLvl);
        if (energy.getEnergyStored() < fePerTick) {
            status = MachineStatus.NO_ENERGY;
            updateLit(level, state, false);
            return;
        }

        energy.consume(fePerTick);
        status = MachineStatus.WORKING;
        workTracker.recordActiveTick(level, MACHINE_ID);
        updateLit(level, state, true);

        int total = currentDuration();
        progress++;
        if (progress >= total) {
            progress = 0;
            rollFishingLoot(level, ownerLvl);
            level.blockEntityChanged(worldPosition);
        }
    }

    private boolean detectWater(ServerLevel level) {
        // Check directly below + all 6 adjacent faces for Fluids.WATER (flowing or source)
        for (Direction d : DIRECTIONS) {
            if (level.getFluidState(this.worldPosition.relative(d)).is(Fluids.WATER)) {
                return true;
            }
        }
        return false;
    }

    private void rollFishingLoot(ServerLevel level, int ownerLvl) {
        ItemStack rod = inventory.getStackInSlot(ROD_SLOT);
        ItemStack tool = rod.isEmpty() ? new ItemStack(Items.FISHING_ROD) : rod;
        int luckEnch = getEnchantLevel(level, rod, Enchantments.LUCK_OF_THE_SEA);
        float luck = AgriLogic.fisherEffectiveLuck(luckEnch, ownerLvl);

        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition))
                .withParameter(LootContextParams.TOOL, tool)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);
        List<ItemStack> loot = table.getRandomItems(params);
        if (loot.isEmpty()) {
            loot = List.of(new ItemStack(Items.COD));
        }
        boolean storedAny = false;
        for (ItemStack item : loot) {
            if (item.isEmpty()) {
                continue;
            }
            ItemStack rem = insertIntoOutputs(item);
            if (rem.getCount() < item.getCount()) {
                storedAny = true;
            }
        }
        if (storedAny) {
            caughtTotal++;
        }
    }

    private int getEnchantLevel(@Nullable Level lvl, ItemStack stack, ResourceKey<Enchantment> key) {
        if (lvl == null || stack.isEmpty()) {
            return 0;
        }
        Optional<Holder.Reference<Enchantment>> holder = lvl.registryAccess()
                .lookup(Registries.ENCHANTMENT)
                .flatMap(reg -> reg.get(key));
        return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    private boolean hasAnyOutputSpace() {
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT; i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private ItemStack insertIntoOutputs(ItemStack stack) {
        ItemStack rem = stack.copy();
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && !rem.isEmpty(); i++) {
            rem = inventory.insertItem(i, rem, false);
        }
        return rem;
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AutoFisherBlock.LIT) && state.getValue(AutoFisherBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(AutoFisherBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(AgriLogic.FISHER_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int currentDuration() {
        int lure = getEnchantLevel(level, inventory.getStackInSlot(ROD_SLOT), Enchantments.LURE);
        int ownerLvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return AgriLogic.fisherDurationTicks(getMotorCount(), lure, ownerLvl);
    }

    public boolean hasWater() {
        return hasWater;
    }

    public int getMotorCount() {
        return inventory.getStackInSlot(MOTOR_SLOT).getCount();
    }

    public int getBatteryCount() {
        return inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return com.industrialcore.level.UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public MachineStatus getStatus() {
        return status;
    }

    @Override
    @Nullable
    public UUID getOwnerUuid() {
        return workTracker.ownerUuid();
    }

    @Override
    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        workTracker.setOwnerUuid(ownerUuid);
        setChanged();
    }

    @Override
    public String getMachineProgressId() {
        return MACHINE_ID;
    }

    public void setCreativeOperator(boolean creative) {
        workTracker.setCreativeOperator(creative);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.auto_fisher");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new AutoFisherMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_CAUGHT, caughtTotal);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        caughtTotal = Math.max(0, tag.getInt(TAG_CAUGHT));
        workTracker.load(tag);
    }

    private static final class FisherInventory extends ItemStackHandler {
        private final AutoFisherBlockEntity owner;

        FisherInventory(AutoFisherBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == ROD_SLOT) {
                return stack.getItem() instanceof FishingRodItem;
            }
            if (slot == MOTOR_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == ROD_SLOT) {
                return 1;
            }
            if (slot == MOTOR_SLOT || slot == BATTERY_SLOT) {
                return owner.getUpgradeLimit();
            }
            return super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == BATTERY_SLOT) {
                owner.syncBatteryCapacity();
            }
            owner.setChanged();
        }
    }

    /** External pipe/hopper handler: extracts caught items (slots 1..9); protects rod &amp; upgrades. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return OUTPUT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot >= 0 && slot < OUTPUT_COUNT)
                    ? inventory.getStackInSlot(OUTPUT_START + slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= OUTPUT_COUNT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(OUTPUT_START + slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
