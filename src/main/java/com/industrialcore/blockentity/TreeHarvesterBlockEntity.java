package com.industrialcore.blockentity;

import com.industrialcore.agri.AgriLogic;
import com.industrialcore.block.TreeHarvesterBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.TreeHarvesterMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 90: BlockEntity for the Automatic Tree Harvester ({@code tree_harvester}).
 *
 * <p>Manages a 9x9 front forestry plot (height up to +24):
 * <ul>
 *   <li>Plants saplings from slots 0..2 on a clean 3-block grid (9 spots) over soil/grass/podzol.</li>
 *   <li>Applies subtle motor-scaled growth pulses to saplings inside the plot.</li>
 *   <li>Detects grown logs ({@code BlockTags.LOGS}) and fells connected logs + adjacent leaves via
 *       bounded BFS (max 256 blocks, max horizontal radius 7 from plot center), auto-recycling
 *       dropped saplings back into slots 0..2 before placing surplus into output slots 3..11.</li>
 * </ul></p>
 */
public class TreeHarvesterBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "tree_harvester";

    public static final int SAPLING_START = 0;
    public static final int SAPLING_COUNT = 3;
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
    public static final int DATA_TREES_FELLED = 5;
    public static final int DATA_COUNT = 6;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_TREES = "TreesFelled";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final TreeInventory inventory = new TreeInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            AgriLogic.TREE_BASE_CAPACITY, AgriLogic.TREE_MAX_RECEIVE);

    private int progress;
    private int treesFelled;
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
                case DATA_TREES_FELLED -> treesFelled;
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

    public TreeHarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TREE_HARVESTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TreeHarvesterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int interval = currentInterval();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int plantCost = AgriLogic.treePlantEnergy(getBatteryCount(), ownerLvl);
        int fellCost = AgriLogic.treeFellEnergy(getBatteryCount(), ownerLvl);

        if (energy.getEnergyStored() < plantCost) {
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

        BlockPos center = plotCenter(state);

        // 1. Check for any grown logs in the 9x9 front plot first -> bounded BFS felling!
        BlockPos logSeed = findFirstLog(level, center);
        if (logSeed != null) {
            if (energy.getEnergyStored() < fellCost) {
                status = MachineStatus.NO_ENERGY;
                updateLit(level, state, false);
                return;
            }
            List<BlockPos> treeBlocks = collectTreeBfs(level, center, logSeed);
            List<ItemStack> allDrops = new ArrayList<>();
            for (BlockPos bp : treeBlocks) {
                BlockState bs = level.getBlockState(bp);
                List<ItemStack> drops = Block.getDrops(bs, level, bp, level.getBlockEntity(bp), null, ItemStack.EMPTY);
                for (ItemStack d : drops) {
                    if (!d.isEmpty()) {
                        allDrops.add(d.copy());
                    }
                }
            }
            if (!canAcceptDrops(allDrops)) {
                status = MachineStatus.OUTPUT_FULL;
                updateLit(level, state, false);
                return;
            }
            energy.consume(fellCost);
            for (BlockPos bp : treeBlocks) {
                level.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            storeDropsWithSaplingRecycle(allDrops);
            treesFelled++;
            status = MachineStatus.WORKING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            setChanged();
            return;
        }

        // 2. Plant a sapling on the next open 3-block grid spot if saplings are loaded
        boolean didWork = tryPlantOneSapling(level, center, plantCost);

        // 3. Apply motor-scaled gentle growth pulses to saplings inside the plot
        int pulses = AgriLogic.treeGrowthPulses(getMotorCount());
        if (pulses > 0 && energy.getEnergyStored() >= plantCost) {
            if (applyGrowthPulses(level, center, pulses)) {
                energy.consume(Math.max(1, plantCost / 2));
                didWork = true;
            }
        }

        if (didWork) {
            status = MachineStatus.WORKING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            setChanged();
        } else {
            status = MachineStatus.IDLE;
            updateLit(level, state, false);
        }
    }

    private BlockPos plotCenter(BlockState state) {
        Direction facing = state.hasProperty(TreeHarvesterBlock.FACING)
                ? state.getValue(TreeHarvesterBlock.FACING) : Direction.NORTH;
        return this.worldPosition.relative(facing, AgriLogic.TREE_FORWARD_CENTER);
    }

    @Nullable
    private BlockPos findFirstLog(ServerLevel level, BlockPos center) {
        int baseY = this.worldPosition.getY();
        int cx = center.getX();
        int cz = center.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int maxDy = Math.min(6, AgriLogic.TREE_MAX_HEIGHT);
        for (int dy = 0; dy <= maxDy; dy++) {
            for (int dz = -AgriLogic.TREE_RANGE_RADIUS; dz <= AgriLogic.TREE_RANGE_RADIUS; dz++) {
                for (int dx = -AgriLogic.TREE_RANGE_RADIUS; dx <= AgriLogic.TREE_RANGE_RADIUS; dx++) {
                    cursor.set(cx + dx, baseY + dy, cz + dz);
                    if (level.getBlockState(cursor).is(BlockTags.LOGS)) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Bounded BFS collecting connected logs and directly adjacent leaves within safe radius
     * ({@value AgriLogic#TREE_BFS_HORIZ_CAP}) and block limit ({@value AgriLogic#TREE_MAX_FELL_BLOCKS}).
     */
    private List<BlockPos> collectTreeBfs(ServerLevel level, BlockPos center, BlockPos start) {
        int baseY = this.worldPosition.getY();
        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> leaves = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && (logs.size() + leaves.size()) < AgriLogic.TREE_MAX_FELL_BLOCKS) {
            BlockPos cur = queue.poll();
            BlockState st = level.getBlockState(cur);
            boolean isLog = st.is(BlockTags.LOGS);
            boolean isLeaf = st.is(BlockTags.LEAVES);
            if (!isLog && !isLeaf) {
                continue;
            }
            if (isLog) {
                logs.add(cur);
            } else {
                leaves.add(cur);
            }

            // Logs expand 3x3x3 to catch diagonal branches (acacia/dark oak/large oak);
            // Leaves expand 6-way only within 3 blocks of logs so neighboring trees stay untouched.
            if (isLog) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            enqueueIfTreePart(level, center, baseY, cur.offset(dx, dy, dz), visited, queue);
                        }
                    }
                }
            } else {
                for (Direction d : Direction.values()) {
                    BlockPos nb = cur.relative(d);
                    if (visited.contains(nb)) {
                        continue;
                    }
                    int rx = nb.getX() - center.getX();
                    int ry = nb.getY() - baseY;
                    int rz = nb.getZ() - center.getZ();
                    if (!AgriLogic.isWithinTreeFellBounds(rx, ry, rz)) {
                        continue;
                    }
                    BlockState nst = level.getBlockState(nb);
                    if (nst.is(BlockTags.LEAVES)) {
                        visited.add(nb);
                        queue.add(nb);
                    }
                }
            }
        }
        List<BlockPos> all = new ArrayList<>(logs.size() + leaves.size());
        all.addAll(logs);
        all.addAll(leaves);
        return all;
    }

    private static void enqueueIfTreePart(ServerLevel level, BlockPos center, int baseY,
                                          BlockPos nb, Set<BlockPos> visited, Deque<BlockPos> queue) {
        if (visited.contains(nb)) {
            return;
        }
        int rx = nb.getX() - center.getX();
        int ry = nb.getY() - baseY;
        int rz = nb.getZ() - center.getZ();
        if (!AgriLogic.isWithinTreeFellBounds(rx, ry, rz)) {
            return;
        }
        BlockState st = level.getBlockState(nb);
        if (st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)) {
            visited.add(nb);
            queue.add(nb);
        }
    }

    private boolean tryPlantOneSapling(ServerLevel level, BlockPos center, int plantCost) {
        int saplingSlot = findSaplingSlot();
        if (saplingSlot < 0 || energy.getEnergyStored() < plantCost) {
            return false;
        }
        ItemStack stack = inventory.getStackInSlot(saplingSlot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState saplingState = blockItem.getBlock().defaultBlockState();
        int baseY = this.worldPosition.getY();

        for (int dz = -AgriLogic.TREE_RANGE_RADIUS; dz <= AgriLogic.TREE_RANGE_RADIUS; dz++) {
            for (int dx = -AgriLogic.TREE_RANGE_RADIUS; dx <= AgriLogic.TREE_RANGE_RADIUS; dx++) {
                if (!AgriLogic.isTreePlantingSpot(dx, dz)) {
                    continue;
                }
                BlockPos p = new BlockPos(center.getX() + dx, baseY, center.getZ() + dz);
                if (!level.getBlockState(p).isAir()) {
                    continue;
                }
                BlockState below = level.getBlockState(p.below());
                if (below.is(BlockTags.DIRT) && saplingState.canSurvive(level, p)) {
                    energy.consume(plantCost);
                    inventory.extractItem(saplingSlot, 1, false);
                    level.setBlock(p, saplingState, Block.UPDATE_ALL);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean applyGrowthPulses(ServerLevel level, BlockPos center, int pulses) {
        int baseY = this.worldPosition.getY();
        List<BlockPos> saplings = new ArrayList<>();
        for (int dz = -AgriLogic.TREE_RANGE_RADIUS; dz <= AgriLogic.TREE_RANGE_RADIUS; dz++) {
            for (int dx = -AgriLogic.TREE_RANGE_RADIUS; dx <= AgriLogic.TREE_RANGE_RADIUS; dx++) {
                BlockPos p = new BlockPos(center.getX() + dx, baseY, center.getZ() + dz);
                if (level.getBlockState(p).getBlock() instanceof SaplingBlock) {
                    saplings.add(p);
                }
            }
        }
        if (saplings.isEmpty()) {
            return false;
        }
        for (int i = 0; i < pulses; i++) {
            BlockPos target = saplings.get(level.random.nextInt(saplings.size()));
            BlockState st = level.getBlockState(target);
            if (st.getBlock() instanceof SaplingBlock sapling) {
                sapling.advanceTree(level, target, st, level.random);
            }
        }
        return true;
    }

    private int findSaplingSlot() {
        for (int i = SAPLING_START; i < SAPLING_START + SAPLING_COUNT; i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (!s.isEmpty() && isSaplingItem(s)) {
                return i;
            }
        }
        return -1;
    }

    private boolean canAcceptDrops(List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return true;
        }
        int totalSlots = SAPLING_COUNT + OUTPUT_COUNT;
        ItemStack[] sim = new ItemStack[totalSlots];
        for (int i = 0; i < totalSlots; i++) {
            sim[i] = inventory.getStackInSlot(i).copy();
        }
        for (ItemStack raw : drops) {
            if (raw.isEmpty()) {
                continue;
            }
            int rem = raw.getCount();
            int max = raw.getMaxStackSize();
            if (isSaplingItem(raw)) {
                rem = simInsertRange(sim, SAPLING_START, SAPLING_START + SAPLING_COUNT, raw, rem, max);
            }
            if (rem > 0) {
                rem = simInsertRange(sim, OUTPUT_START, OUTPUT_START + OUTPUT_COUNT, raw, rem, max);
            }
            if (rem > 0) {
                return false;
            }
        }
        return true;
    }

    private static int simInsertRange(ItemStack[] sim, int start, int end,
                                      ItemStack proto, int rem, int max) {
        for (int i = start; i < end && rem > 0; i++) {
            if (!sim[i].isEmpty() && ItemStack.isSameItemSameComponents(sim[i], proto)
                    && sim[i].getCount() < max) {
                int add = Math.min(rem, max - sim[i].getCount());
                sim[i].grow(add);
                rem -= add;
            }
        }
        for (int i = start; i < end && rem > 0; i++) {
            if (sim[i].isEmpty()) {
                int add = Math.min(rem, max);
                sim[i] = proto.copyWithCount(add);
                rem -= add;
            }
        }
        return rem;
    }

    private void storeDropsWithSaplingRecycle(List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            ItemStack rem = drop.copy();
            if (isSaplingItem(rem)) {
                for (int i = SAPLING_START; i < SAPLING_START + SAPLING_COUNT && !rem.isEmpty(); i++) {
                    rem = inventory.insertItem(i, rem, false);
                }
            }
            for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && !rem.isEmpty(); i++) {
                rem = inventory.insertItem(i, rem, false);
            }
        }
    }

    public static boolean isSaplingItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.SAPLINGS);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(TreeHarvesterBlock.LIT) && state.getValue(TreeHarvesterBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(TreeHarvesterBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(AgriLogic.TREE_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int currentInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return AgriLogic.treeCycleInterval(getMotorCount(), lvl);
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
        return Component.translatable("block.industrialcore.tree_harvester");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new TreeHarvesterMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_TREES, treesFelled);
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
        treesFelled = Math.max(0, tag.getInt(TAG_TREES));
        workTracker.load(tag);
    }

    private static final class TreeInventory extends ItemStackHandler {
        private final TreeHarvesterBlockEntity owner;

        TreeInventory(TreeHarvesterBlockEntity owner) {
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
            if (slot >= SAPLING_START && slot < SAPLING_START + SAPLING_COUNT) {
                return isSaplingItem(stack);
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

    /** External pipe/hopper handler: allows sapling inserts (0..2) &amp; output extracts (3..11); protects upgrades. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SAPLING_COUNT + OUTPUT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot >= 0 && slot < getSlots()) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < SAPLING_START || slot >= SAPLING_START + SAPLING_COUNT) {
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
            return slot >= SAPLING_START && slot < SAPLING_START + SAPLING_COUNT && isSaplingItem(stack);
        }
    }
}
