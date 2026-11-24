package com.industrialcore.blockentity;

import com.industrialcore.agri.AgriWave2Logic;
import com.industrialcore.block.RanchHarvesterBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.RanchHarvesterMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 91: BlockEntity for the Automatic Ranch Harvester ({@code ranch_harvester}).
 *
 * <p>Scans a 9x9 volume centered on the machine (height -2..+4) for livestock:
 * <ul>
 *   <li>Shears ready adult sheep cleanly into colored wool without spawning loose drops.</li>
 *   <li>Milks adult cows (using Buckets) and milks/stews mooshrooms (using Bowls or Buckets).</li>
 *   <li>Automatically feeds breeding pairs matching food from slots 0..2, enforcing a strict
 *       12-animal per-species lag-prevention cap.</li>
 * </ul></p>
 */
public class RanchHarvesterBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "ranch_harvester";

    public static final int FEED_START = 0;
    public static final int FEED_COUNT = 3;
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
    public static final int DATA_ANIMALS = 5;
    public static final int DATA_BREED_CAPPED = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final RanchInventory inventory = new RanchInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            AgriWave2Logic.RANCH_BASE_CAPACITY, AgriWave2Logic.RANCH_MAX_RECEIVE);

    private int progress;
    private int detectedAnimals;
    private boolean breedingCapped;
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
                case DATA_ANIMALS -> detectedAnimals;
                case DATA_BREED_CAPPED -> breedingCapped ? 1 : 0;
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

    public RanchHarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RANCH_HARVESTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RanchHarvesterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int interval = currentInterval();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int workCost = AgriWave2Logic.ranchWorkEnergy(getBatteryCount(), ownerLvl);

        if (energy.getEnergyStored() < workCost) {
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

        List<Animal> animals = level.getEntitiesOfClass(Animal.class, ranchBox(), Entity::isAlive);
        detectedAnimals = animals.size();

        Map<EntityType<?>, List<Animal>> bySpecies = new LinkedHashMap<>();
        for (Animal animal : animals) {
            bySpecies.computeIfAbsent(animal.getType(), k -> new ArrayList<>()).add(animal);
        }
        boolean anyCapReached = false;
        for (List<Animal> list : bySpecies.values()) {
            if (!AgriWave2Logic.canBreedSpecies(list.size())) {
                anyCapReached = true;
                break;
            }
        }
        breedingCapped = anyCapReached;

        boolean didWork = false;
        boolean blockedByOutput = false;

        // Category 1: Shear ready sheep cleanly (independent of milking/breeding)
        if (energy.getEnergyStored() >= workCost) {
            for (Animal animal : animals) {
                if (animal instanceof Sheep sheep && sheep.readyForShearing()) {
                    int count = 1 + level.random.nextInt(3);
                    ItemStack wool = new ItemStack(woolForColor(sheep.getColor()), count);
                    if (!canInsertOutput(wool)) {
                        blockedByOutput = true;
                        continue;
                    }
                    sheep.setSheared(true);
                    energy.consume(workCost);
                    insertOutput(wool);
                    level.playSound(null, sheep.blockPosition(), SoundEvents.SHEEP_SHEAR,
                            SoundSource.BLOCKS, 0.35F, 1.0F);
                    didWork = true;
                    break;
                }
            }
        }

        // Category 2: Milk cows / harvest mooshroom stew if container available
        if (energy.getEnergyStored() >= workCost) {
            boolean milkedThisCycle = false;
            int bowlSlot = findInputSlot(Items.BOWL);
            if (bowlSlot >= 0) {
                for (Animal animal : animals) {
                    if (animal instanceof MushroomCow mc && !mc.isBaby()) {
                        ItemStack stew = new ItemStack(Items.MUSHROOM_STEW);
                        if (!canInsertOutput(stew)) {
                            blockedByOutput = true;
                            continue;
                        }
                        inventory.extractItem(bowlSlot, 1, false);
                        energy.consume(workCost);
                        insertOutput(stew);
                        didWork = true;
                        milkedThisCycle = true;
                        break;
                    }
                }
            }
            if (!milkedThisCycle && energy.getEnergyStored() >= workCost) {
                int bucketSlot = findInputSlot(Items.BUCKET);
                if (bucketSlot >= 0) {
                    for (Animal animal : animals) {
                        if (animal instanceof Cow cow && !cow.isBaby()) {
                            ItemStack milk = new ItemStack(Items.MILK_BUCKET);
                            if (!canInsertOutput(milk)) {
                                blockedByOutput = true;
                                continue;
                            }
                            inventory.extractItem(bucketSlot, 1, false);
                            energy.consume(workCost);
                            insertOutput(milk);
                            didWork = true;
                            break;
                        }
                    }
                }
            }
        }

        // Category 3: Auto-breed same-species adult pairs respecting the 12-animal species cap
        if (energy.getEnergyStored() >= workCost) {
            for (List<Animal> speciesList : bySpecies.values()) {
                if (!AgriWave2Logic.canBreedSpecies(speciesList.size())) {
                    continue;
                }
                Animal first = null;
                Animal second = null;
                for (Animal candidate : speciesList) {
                    if (!candidate.isBaby() && candidate.getAge() == 0 && candidate.canFallInLove()) {
                        if (first == null) {
                            first = candidate;
                        } else {
                            second = candidate;
                            break;
                        }
                    }
                }
                if (first != null && second != null && consumePairFood(first)) {
                    first.setInLove(null);
                    second.setInLove(null);
                    energy.consume(workCost);
                    didWork = true;
                    break;
                }
            }
        }

        if (didWork) {
            completeAction(level, state);
        } else {
            status = blockedByOutput ? MachineStatus.OUTPUT_FULL : MachineStatus.IDLE;
            updateLit(level, state, false);
        }
    }

    private void completeAction(ServerLevel level, BlockState state) {
        status = MachineStatus.WORKING;
        workTracker.recordActiveTick(level, MACHINE_ID);
        updateLit(level, state, true);
        setChanged();
    }

    private AABB ranchBox() {
        double r = AgriWave2Logic.RANCH_RADIUS;
        return new AABB(
                worldPosition.getX() - r,
                worldPosition.getY() + AgriWave2Logic.RANCH_MIN_DY,
                worldPosition.getZ() - r,
                worldPosition.getX() + r + 1.0D,
                worldPosition.getY() + AgriWave2Logic.RANCH_MAX_DY + 1.0D,
                worldPosition.getZ() + r + 1.0D
        );
    }

    private int findInputSlot(Item item) {
        for (int i = FEED_START; i < FEED_START + FEED_COUNT; i++) {
            ItemStack st = inventory.getStackInSlot(i);
            if (!st.isEmpty() && st.is(item)) {
                return i;
            }
        }
        return -1;
    }

    private boolean consumePairFood(Animal animal) {
        int available = 0;
        for (int i = FEED_START; i < FEED_START + FEED_COUNT; i++) {
            ItemStack st = inventory.getStackInSlot(i);
            if (!st.isEmpty() && animal.isFood(st)) {
                available += st.getCount();
            }
        }
        if (available < 2) {
            return false;
        }
        int remaining = 2;
        for (int i = FEED_START; i < FEED_START + FEED_COUNT && remaining > 0; i++) {
            ItemStack st = inventory.getStackInSlot(i);
            if (!st.isEmpty() && animal.isFood(st)) {
                int take = Math.min(remaining, st.getCount());
                inventory.extractItem(i, take, false);
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    private boolean canInsertOutput(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        int rem = stack.getCount();
        int max = stack.getMaxStackSize();
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && rem > 0; i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            if (existing.isEmpty()) {
                rem -= max;
            } else if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < max) {
                rem -= (max - existing.getCount());
            }
        }
        return rem <= 0;
    }

    private void insertOutput(ItemStack stack) {
        ItemStack rem = stack.copy();
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && !rem.isEmpty(); i++) {
            rem = inventory.insertItem(i, rem, false);
        }
    }

    private static Item woolForColor(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_WOOL;
            case ORANGE -> Items.ORANGE_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
            case LIME -> Items.LIME_WOOL;
            case PINK -> Items.PINK_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case RED -> Items.RED_WOOL;
            case BLACK -> Items.BLACK_WOOL;
        };
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(RanchHarvesterBlock.LIT) && state.getValue(RanchHarvesterBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(RanchHarvesterBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(AgriWave2Logic.RANCH_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int currentInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return AgriWave2Logic.ranchCycleInterval(getMotorCount(), lvl);
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
        return Component.translatable("block.industrialcore.ranch_harvester");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new RanchHarvesterMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
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
        workTracker.load(tag);
    }

    public static boolean isRanchInput(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(ModItems.MOTOR.get()) && !stack.is(ModItems.BATTERY.get());
    }

    private static final class RanchInventory extends ItemStackHandler {
        private final RanchHarvesterBlockEntity owner;

        RanchInventory(RanchHarvesterBlockEntity owner) {
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
            if (slot >= FEED_START && slot < FEED_START + FEED_COUNT) {
                return isRanchInput(stack);
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

    /** External automation: insert into input slots 0..2 &amp; extract from output 3..11; upgrades protected. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return FEED_COUNT + OUTPUT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot >= 0 && slot < getSlots()) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < FEED_START || slot >= FEED_START + FEED_COUNT || !isRanchInput(stack)) {
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
            return slot >= FEED_START && slot < FEED_START + FEED_COUNT && isRanchInput(stack);
        }
    }
}
