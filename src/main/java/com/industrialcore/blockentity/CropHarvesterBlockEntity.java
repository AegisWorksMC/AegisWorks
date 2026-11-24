package com.industrialcore.blockentity;

import com.industrialcore.agri.AgriLogic;
import com.industrialcore.block.CropHarvesterBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.CropHarvesterMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 90: BlockEntity for the Automatic Crop Harvester ({@code crop_harvester}).
 *
 * <p>Scans a 9x9 front volume (dy -1..+2), harvests mature crops / nether wart / melons / pumpkins /
 * tall sugar cane &amp; bamboo / sweet berries into 9 internal output slots, and automatically
 * replants using harvested seeds or the 3 dedicated seed buffer slots. Pauses cleanly if output
 * slots are full so zero items are ever dropped or lost.</p>
 */
public class CropHarvesterBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "crop_harvester";

    public static final int SEED_START = 0;
    public static final int SEED_COUNT = 3;
    public static final int OUTPUT_START = 3;
    public static final int OUTPUT_COUNT = 9;
    public static final int MOTOR_SLOT = 12;
    public static final int BATTERY_SLOT = 13;
    public static final int SLOT_COUNT = 14;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_HARVESTED = 5;
    public static final int DATA_COUNT = 6;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_HARVESTED = "Harvested";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final HarvesterInventory inventory = new HarvesterInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            AgriLogic.CROP_BASE_CAPACITY, AgriLogic.CROP_MAX_RECEIVE);

    private int progress;
    private int harvestedTotal;
    private MachineStatus status = MachineStatus.IDLE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> currentInterval();
                case DATA_HARVESTED -> harvestedTotal;
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

    public CropHarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CROP_HARVESTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CropHarvesterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int interval = currentInterval();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int harvestCost = AgriLogic.cropHarvestEnergy(getBatteryCount(), ownerLvl);

        if (energy.getEnergyStored() < harvestCost) {
            status = MachineStatus.NO_ENERGY;
            progress = 0;
            updateLit(level, state, false);
            return;
        }

        progress++;
        if (progress < interval) {
            if (status == MachineStatus.NO_ENERGY) {
                status = MachineStatus.IDLE;
            }
            return;
        }
        progress = 0;

        BlockPos target = findNextMatureCrop(level, state);
        if (target == null) {
            status = MachineStatus.IDLE;
            updateLit(level, state, false);
            return;
        }

        BlockState targetState = level.getBlockState(target);
        List<ItemStack> drops = computeDrops(level, target, targetState);
        // Check if we can reserve 1 seed for replanting directly from drops or seed slots
        ReplantPlan plan = planReplant(targetState, drops);
        List<ItemStack> toStore = plan.remainingDrops();

        if (!canInsertAllOutputs(toStore)) {
            status = MachineStatus.OUTPUT_FULL;
            updateLit(level, state, false);
            return;
        }

        // Pay FE, execute harvest, store drops, and replant cleanly
        energy.consume(harvestCost);
        applyHarvestAndReplant(level, target, targetState, plan);
        for (ItemStack drop : toStore) {
            insertIntoOutputs(drop);
        }

        harvestedTotal++;
        status = MachineStatus.WORKING;
        workTracker.recordActiveTick(level, MACHINE_ID);
        updateLit(level, state, true);
        setChanged();
    }

    private BlockPos plotCenter(BlockState state) {
        Direction facing = state.hasProperty(CropHarvesterBlock.FACING)
                ? state.getValue(CropHarvesterBlock.FACING) : Direction.NORTH;
        return this.worldPosition.relative(facing, AgriLogic.CROP_FORWARD_CENTER);
    }

    @Nullable
    private BlockPos findNextMatureCrop(ServerLevel level, BlockState state) {
        BlockPos center = plotCenter(state);
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = AgriLogic.CROP_MAX_DY; dy >= AgriLogic.CROP_MIN_DY; dy--) {
            for (int dz = -AgriLogic.CROP_RANGE_RADIUS; dz <= AgriLogic.CROP_RANGE_RADIUS; dz++) {
                for (int dx = -AgriLogic.CROP_RANGE_RADIUS; dx <= AgriLogic.CROP_RANGE_RADIUS; dx++) {
                    cursor.set(cx + dx, cy + dy, cz + dz);
                    if (isHarvestable(level, cursor, level.getBlockState(cursor))) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static boolean isHarvestable(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (state.is(Blocks.MELON) || block instanceof PumpkinBlock) {
            return true;
        }
        if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        if (block instanceof SugarCaneBlock || block instanceof BambooStalkBlock || block instanceof CactusBlock) {
            // Only harvest 2nd+ segment so the base stalk regrows automatically!
            return level.getBlockState(pos.below()).is(block);
        }
        return false;
    }

    private static List<ItemStack> computeDrops(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof SweetBerryBushBlock) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            int count = age >= 3 ? (2 + level.random.nextInt(2)) : (1 + level.random.nextInt(2));
            return List.of(new ItemStack(Items.SWEET_BERRIES, count));
        }
        return new ArrayList<>(Block.getDrops(state, level, pos, level.getBlockEntity(pos), null, ItemStack.EMPTY));
    }

    private record ReplantPlan(@Nullable BlockState replantState, int seedSlotToConsume,
                               List<ItemStack> remainingDrops) {}

    private ReplantPlan planReplant(BlockState harvestedState, List<ItemStack> rawDrops) {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack s : rawDrops) {
            if (!s.isEmpty()) {
                drops.add(s.copy());
            }
        }
        Block block = harvestedState.getBlock();
        if (block instanceof SweetBerryBushBlock) {
            return new ReplantPlan(harvestedState.setValue(SweetBerryBushBlock.AGE, 1), -1, drops);
        }

        BlockState freshState = null;
        Item requiredSeed = null;
        if (block instanceof CropBlock crop) {
            freshState = crop.getStateForAge(0);
            requiredSeed = block.asItem();
            if (requiredSeed == Items.AIR) {
                // Fallback for blocks like wheat whose Block.asItem() is WHEAT_SEEDS via cloneItemStack
                ItemStack pick = new ItemStack(block);
                if (!pick.isEmpty()) {
                    requiredSeed = pick.getItem();
                }
            }
        } else if (block instanceof NetherWartBlock) {
            freshState = Blocks.NETHER_WART.defaultBlockState();
            requiredSeed = Items.NETHER_WART;
        }

        if (freshState == null) {
            return new ReplantPlan(null, -1, drops);
        }

        // 1. Prefer consuming 1 matching seed directly from the harvested drops
        for (int i = 0; i < drops.size(); i++) {
            ItemStack d = drops.get(i);
            if (isMatchingSeed(d, block, requiredSeed)) {
                d.shrink(1);
                if (d.isEmpty()) {
                    drops.remove(i);
                }
                return new ReplantPlan(freshState, -1, drops);
            }
        }

        // 2. Otherwise consume 1 matching seed from the harvester's 3 seed buffer slots
        for (int s = SEED_START; s < SEED_START + SEED_COUNT; s++) {
            ItemStack buf = inventory.getStackInSlot(s);
            if (!buf.isEmpty() && isMatchingSeed(buf, block, requiredSeed)) {
                return new ReplantPlan(freshState, s, drops);
            }
        }

        return new ReplantPlan(null, -1, drops);
    }

    private static boolean isMatchingSeed(ItemStack stack, Block cropBlock, @Nullable Item requiredSeed) {
        if (stack.isEmpty()) {
            return false;
        }
        if (requiredSeed != null && requiredSeed != Items.AIR && stack.is(requiredSeed)) {
            return true;
        }
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() == cropBlock) {
            return true;
        }
        return cropBlock == Blocks.WHEAT && stack.is(Items.WHEAT_SEEDS);
    }

    private void applyHarvestAndReplant(ServerLevel level, BlockPos target,
                                        BlockState oldState, ReplantPlan plan) {
        if (plan.seedSlotToConsume() >= 0) {
            inventory.extractItem(plan.seedSlotToConsume(), 1, false);
        }
        if (plan.replantState() != null) {
            level.setBlock(target, plan.replantState(), Block.UPDATE_ALL);
        } else {
            level.destroyBlock(target, false);
        }
    }

    private boolean canInsertAllOutputs(List<ItemStack> items) {
        if (items.isEmpty()) {
            return true;
        }
        ItemStack[] sim = new ItemStack[OUTPUT_COUNT];
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            sim[i] = inventory.getStackInSlot(OUTPUT_START + i).copy();
        }
        for (ItemStack raw : items) {
            if (raw.isEmpty()) {
                continue;
            }
            int rem = raw.getCount();
            int max = raw.getMaxStackSize();
            for (int i = 0; i < OUTPUT_COUNT && rem > 0; i++) {
                if (!sim[i].isEmpty() && ItemStack.isSameItemSameComponents(sim[i], raw)
                        && sim[i].getCount() < max) {
                    int add = Math.min(rem, max - sim[i].getCount());
                    sim[i].grow(add);
                    rem -= add;
                }
            }
            for (int i = 0; i < OUTPUT_COUNT && rem > 0; i++) {
                if (sim[i].isEmpty()) {
                    int add = Math.min(rem, max);
                    sim[i] = raw.copyWithCount(add);
                    rem -= add;
                }
            }
            if (rem > 0) {
                return false;
            }
        }
        return true;
    }

    private void insertIntoOutputs(ItemStack stack) {
        ItemStack rem = stack.copy();
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && !rem.isEmpty(); i++) {
            rem = inventory.insertItem(i, rem, false);
        }
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(CropHarvesterBlock.LIT) && state.getValue(CropHarvesterBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(CropHarvesterBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(AgriLogic.CROP_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int currentInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return AgriLogic.cropScanInterval(getMotorCount(), lvl);
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
        return Component.translatable("block.industrialcore.crop_harvester");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new CropHarvesterMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_HARVESTED, harvestedTotal);
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
        harvestedTotal = Math.max(0, tag.getInt(TAG_HARVESTED));
        workTracker.load(tag);
    }

    private static final class HarvesterInventory extends ItemStackHandler {
        private final CropHarvesterBlockEntity owner;

        HarvesterInventory(CropHarvesterBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == MOTOR_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            if (slot >= SEED_START && slot < SEED_START + SEED_COUNT) {
                return isSeedLike(stack);
            }
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
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

    public static boolean isSeedLike(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BEETROOT_SEEDS)
                || stack.is(Items.CARROT) || stack.is(Items.POTATO)
                || stack.is(Items.NETHER_WART) || stack.is(Items.TORCHFLOWER_SEEDS)
                || stack.is(Items.PITCHER_POD) || stack.is(Items.SWEET_BERRIES)) {
            return true;
        }
        return stack.getItem() instanceof BlockItem bi
                && (bi.getBlock() instanceof CropBlock || bi.getBlock() instanceof NetherWartBlock);
    }

    /** External pipe/hopper handler: allows seed inserts (0..2) &amp; output extracts (3..11); protects upgrades. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SEED_COUNT + OUTPUT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot >= 0 && slot < getSlots()) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < SEED_START || slot >= SEED_START + SEED_COUNT) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < OUTPUT_START || slot >= OUTPUT_START + OUTPUT_COUNT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= SEED_START && slot < SEED_START + SEED_COUNT && isSeedLike(stack);
        }
    }
}
