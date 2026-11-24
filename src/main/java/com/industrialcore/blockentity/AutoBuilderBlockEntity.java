package com.industrialcore.blockentity;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.life.LifeLayout;
import com.industrialcore.life.LifeLogic;
import com.industrialcore.life.LifeLogic.BuildMode;
import com.industrialcore.life.LifeLogic.LocalOffset;
import com.industrialcore.life.LifeLogic.WorldDelta;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.AutoBuilderMenu;
import com.industrialcore.network.AutoBuilderActionPayload;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Phase 94: BlockEntity for the Auto Builder ({@code auto_builder}).
 *
 * <p>Places safe normal blocks from its 9-slot material bay into a configurable FLOOR, WALL, or
 * HOLLOW_BOX volume strictly in front of the machine without ever breaking or overwriting
 * non-replaceable existing blocks.</p>
 */
public class AutoBuilderBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "auto_builder";

    public static final int MAT_START = 0;
    public static final int MAT_COUNT = LifeLayout.BUILDER_MAT_COUNT; // 9
    public static final int MOTOR_SLOT = 9;
    public static final int BATTERY_SLOT = 10;
    public static final int SLOT_COUNT = 11;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_RUNNING = 3;
    public static final int DATA_MODE = 4;
    public static final int DATA_WIDTH = 5;
    public static final int DATA_DEPTH = 6;
    public static final int DATA_HEIGHT = 7;
    public static final int DATA_PLACED = 8;
    public static final int DATA_TOTAL = 9;
    public static final int DATA_COUNT = 10;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_RUNNING = "Running";
    private static final String TAG_MODE = "Mode";
    private static final String TAG_WIDTH = "Width";
    private static final String TAG_DEPTH = "Depth";
    private static final String TAG_HEIGHT = "Height";
    private static final String TAG_SCAN = "ScanIndex";
    private static final String TAG_PLACED = "PlacedCount";
    private static final String TAG_STATE = "BuilderState";
    private static final String TAG_INVENTORY = "Inventory";

    private static final int MAX_SKIPS_PER_TICK = 64;

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final BuilderInventory inventory = new BuilderInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            LifeLogic.BUILDER_BASE_CAPACITY, LifeLogic.BUILDER_MAX_RECEIVE);

    private boolean running;
    private BuildMode mode = BuildMode.FLOOR;
    private int width = LifeLogic.BUILDER_DEFAULT_W;
    private int depth = LifeLogic.BUILDER_DEFAULT_D;
    private int height = LifeLogic.BUILDER_DEFAULT_H;
    private int scanIndex;
    private int placedCount;
    private int builderState = LifeLogic.BUILDER_STATE_IDLE;
    private int tickCooldown;
    @Nullable
    private List<LocalOffset> cachedOffsets;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATE -> builderState;
                case DATA_RUNNING -> running ? 1 : 0;
                case DATA_MODE -> mode.id();
                case DATA_WIDTH -> width;
                case DATA_DEPTH -> depth;
                case DATA_HEIGHT -> height;
                case DATA_PLACED -> Math.min(scanIndex, currentTotalBlocks());
                case DATA_TOTAL -> currentTotalBlocks();
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

    public AutoBuilderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_BUILDER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoBuilderBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    /** True when {@code stack} is a safe, placeable ordinary block (no block entities, multi-blocks, or hazards). */
    public static boolean isSafeBuildableStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) {
            return false;
        }
        Block block = bi.getBlock();
        if (block instanceof EntityBlock
                || block instanceof BedBlock
                || block instanceof DoorBlock
                || block instanceof DoublePlantBlock
                || block instanceof TntBlock
                || block instanceof LiquidBlock
                || block instanceof BaseFireBlock
                || block instanceof BarrierBlock
                || block instanceof LightBlock
                || block instanceof CommandBlock
                || block instanceof StructureBlock
                || block instanceof JigsawBlock) {
            return false;
        }
        return !block.defaultBlockState().isAir();
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        if (!running) {
            if (builderState != LifeLogic.BUILDER_STATE_COMPLETED) {
                builderState = LifeLogic.BUILDER_STATE_IDLE;
            }
            updateLit(level, state, false);
            return;
        }

        int motors = getMotorCount();
        int batteries = getBatteryCount();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int energyCost = LifeLogic.builderEnergyPerBlock(batteries, ownerLvl);
        if (energy.getEnergyStored() < energyCost) {
            builderState = LifeLogic.BUILDER_STATE_NO_ENERGY;
            updateLit(level, state, false);
            return;
        }

        List<LocalOffset> offsets = patternOffsets();
        if (scanIndex >= offsets.size()) {
            running = false;
            builderState = LifeLogic.BUILDER_STATE_COMPLETED;
            updateLit(level, state, false);
            setChanged();
            return;
        }

        tickCooldown++;
        int interval = LifeLogic.builderInterval(motors, ownerLvl);
        if (tickCooldown < interval) {
            builderState = LifeLogic.BUILDER_STATE_BUILDING;
            updateLit(level, state, true);
            return;
        }
        tickCooldown = 0;

        Direction facing = state.hasProperty(AbstractLifeBlock.FACING)
                ? state.getValue(AbstractLifeBlock.FACING) : Direction.NORTH;
        int fdx = facing.getStepX();
        int fdz = facing.getStepZ();

        int batch = LifeLogic.builderBatchSize(motors);
        int placedThisTick = 0;
        int skips = 0;
        boolean outOfMaterial = false;
        boolean outOfEnergy = false;

        while (placedThisTick < batch && scanIndex < offsets.size() && skips < MAX_SKIPS_PER_TICK) {
            WorldDelta delta = LifeLogic.toWorldDelta(fdx, fdz, offsets.get(scanIndex));
            BlockPos targetPos = worldPosition.offset(delta.dx(), delta.dy(), delta.dz());
            if (!level.isInWorldBounds(targetPos) || !level.isLoaded(targetPos)) {
                scanIndex++;
                skips++;
                continue;
            }
            BlockState existing = level.getBlockState(targetPos);
            // Strict safety guard: never destroy or overwrite non-replaceable blocks!
            if (!existing.canBeReplaced()) {
                scanIndex++;
                skips++;
                continue;
            }
            if (energy.getEnergyStored() < energyCost) {
                outOfEnergy = true;
                break;
            }
            int matSlot = findMaterialSlot();
            if (matSlot < 0) {
                outOfMaterial = true;
                break;
            }
            ItemStack matStack = inventory.getStackInSlot(matSlot);
            Block placeBlock = ((BlockItem) matStack.getItem()).getBlock();
            BlockState placeState = placeBlock.defaultBlockState();
            if (level.setBlock(targetPos, placeState, Block.UPDATE_ALL)) {
                inventory.extractItem(matSlot, 1, false);
                energy.consume(energyCost);
                placedCount++;
                placedThisTick++;
                if (placedThisTick == 1) {
                    SoundType st = placeState.getSoundType(level, targetPos, null);
                    level.playSound(null, targetPos, st.getPlaceSound(), SoundSource.BLOCKS,
                            0.35F, st.getPitch() * 0.95F);
                }
            }
            scanIndex++;
        }

        if (scanIndex >= offsets.size()) {
            running = false;
            builderState = LifeLogic.BUILDER_STATE_COMPLETED;
            updateLit(level, state, false);
            if (placedThisTick > 0) {
                workTracker.recordActiveTick(level, MACHINE_ID);
            }
            setChanged();
        } else if (outOfEnergy) {
            builderState = LifeLogic.BUILDER_STATE_NO_ENERGY;
            updateLit(level, state, false);
        } else if (outOfMaterial) {
            builderState = LifeLogic.BUILDER_STATE_NO_MATERIAL;
            updateLit(level, state, false);
        } else {
            builderState = LifeLogic.BUILDER_STATE_BUILDING;
            updateLit(level, state, true);
            if (placedThisTick > 0) {
                workTracker.recordActiveTick(level, MACHINE_ID);
                setChanged();
            }
        }
    }

    private int findMaterialSlot() {
        for (int i = MAT_START; i < MAT_START + MAT_COUNT; i++) {
            if (isSafeBuildableStack(inventory.getStackInSlot(i))) {
                return i;
            }
        }
        return -1;
    }

    private List<LocalOffset> patternOffsets() {
        if (cachedOffsets == null) {
            cachedOffsets = LifeLogic.patternOffsets(mode, width, depth, height);
        }
        return cachedOffsets;
    }

    public int currentTotalBlocks() {
        return LifeLogic.patternCount(mode, width, depth, height);
    }

    public void handleAction(int action) {
        switch (action) {
            case AutoBuilderActionPayload.ACTION_TOGGLE_RUN -> {
                if (!running && (builderState == LifeLogic.BUILDER_STATE_COMPLETED
                        || scanIndex >= currentTotalBlocks())) {
                    scanIndex = 0;
                    placedCount = 0;
                }
                running = !running;
                builderState = running ? LifeLogic.BUILDER_STATE_BUILDING : LifeLogic.BUILDER_STATE_IDLE;
            }
            case AutoBuilderActionPayload.ACTION_CYCLE_MODE -> {
                mode = mode.next();
                invalidatePattern();
            }
            case AutoBuilderActionPayload.ACTION_RESET -> {
                running = false;
                scanIndex = 0;
                placedCount = 0;
                builderState = LifeLogic.BUILDER_STATE_IDLE;
            }
            case AutoBuilderActionPayload.ACTION_W_DEC -> {
                width = LifeLogic.clampWidth(width - 1);
                invalidatePattern();
            }
            case AutoBuilderActionPayload.ACTION_W_INC -> {
                width = LifeLogic.clampWidth(width + 1);
                invalidatePattern();
            }
            case AutoBuilderActionPayload.ACTION_D_DEC -> {
                depth = LifeLogic.clampDepth(depth - 1);
                invalidatePattern();
            }
            case AutoBuilderActionPayload.ACTION_D_INC -> {
                depth = LifeLogic.clampDepth(depth + 1);
                invalidatePattern();
            }
            case AutoBuilderActionPayload.ACTION_H_DEC -> {
                height = LifeLogic.clampHeight(height - 1);
                invalidatePattern();
            }
            case AutoBuilderActionPayload.ACTION_H_INC -> {
                height = LifeLogic.clampHeight(height + 1);
                invalidatePattern();
            }
            default -> {
                return;
            }
        }
        setChanged();
    }

    private void invalidatePattern() {
        cachedOffsets = null;
        scanIndex = 0;
        placedCount = 0;
        if (builderState == LifeLogic.BUILDER_STATE_COMPLETED) {
            builderState = running ? LifeLogic.BUILDER_STATE_BUILDING : LifeLogic.BUILDER_STATE_IDLE;
        }
    }

    public void configureForTest(BuildMode newMode, int w, int d, int h, boolean start) {
        this.mode = newMode == null ? BuildMode.FLOOR : newMode;
        this.width = LifeLogic.clampWidth(w);
        this.depth = LifeLogic.clampDepth(d);
        this.height = LifeLogic.clampHeight(h);
        this.cachedOffsets = null;
        this.scanIndex = 0;
        this.placedCount = 0;
        this.running = start;
        this.builderState = start ? LifeLogic.BUILDER_STATE_BUILDING : LifeLogic.BUILDER_STATE_IDLE;
        setChanged();
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractLifeBlock.LIT) && state.getValue(AbstractLifeBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractLifeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(LifeLogic.BUILDER_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
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
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public boolean isRunning() {
        return running;
    }

    public BuildMode getMode() {
        return mode;
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public int getHeight() {
        return height;
    }

    public int getPlacedCount() {
        return placedCount;
    }

    public int getBuilderState() {
        return builderState;
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.auto_builder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new AutoBuilderMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putBoolean(TAG_RUNNING, running);
        tag.putInt(TAG_MODE, mode.id());
        tag.putInt(TAG_WIDTH, width);
        tag.putInt(TAG_DEPTH, depth);
        tag.putInt(TAG_HEIGHT, height);
        tag.putInt(TAG_SCAN, scanIndex);
        tag.putInt(TAG_PLACED, placedCount);
        tag.putInt(TAG_STATE, builderState);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        running = tag.getBoolean(TAG_RUNNING);
        mode = BuildMode.byId(tag.getInt(TAG_MODE));
        width = LifeLogic.clampWidth(tag.contains(TAG_WIDTH) ? tag.getInt(TAG_WIDTH) : LifeLogic.BUILDER_DEFAULT_W);
        depth = LifeLogic.clampDepth(tag.contains(TAG_DEPTH) ? tag.getInt(TAG_DEPTH) : LifeLogic.BUILDER_DEFAULT_D);
        height = LifeLogic.clampHeight(tag.contains(TAG_HEIGHT) ? tag.getInt(TAG_HEIGHT) : LifeLogic.BUILDER_DEFAULT_H);
        scanIndex = Math.max(0, tag.getInt(TAG_SCAN));
        placedCount = Math.max(0, tag.getInt(TAG_PLACED));
        builderState = tag.getInt(TAG_STATE);
        cachedOffsets = null;
        workTracker.load(tag);
    }

    private static final class BuilderInventory extends ItemStackHandler {
        private final AutoBuilderBlockEntity owner;

        BuilderInventory(AutoBuilderBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot >= MAT_START && slot < MAT_START + MAT_COUNT) {
                return isSafeBuildableStack(stack);
            }
            if (slot == MOTOR_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            return false;
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

    /** External pipe/hopper view: inserts buildable blocks into slots 0..8 only; upgrades protected. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return MAT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot >= 0 && slot < MAT_COUNT) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= MAT_COUNT || !isSafeBuildableStack(stack)) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return (slot >= 0 && slot < MAT_COUNT) ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < MAT_COUNT && isSafeBuildableStack(stack);
        }
    }
}
