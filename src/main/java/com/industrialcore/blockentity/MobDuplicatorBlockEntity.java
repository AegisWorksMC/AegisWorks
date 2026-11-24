package com.industrialcore.blockentity;

import com.industrialcore.block.MobDuplicatorBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.item.MobCapsuleItem;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.MobDuplicatorMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.trap.TrapWave2Logic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Phase 93: BlockEntity for the Mob Automatic Spawner ({@code mob_duplicator}).
 *
 * <p>Reads a recorded {@link MobCapsuleItem} without consuming it, enforces a 9x9 crowding guard
 * (16-mob limit), and spawns 1..4 mobs per cycle as {@link MobSpawnType#SPAWNER} into safe empty
 * spots inside the surrounding 7x7 area.</p>
 */
public class MobDuplicatorBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "mob_duplicator";

    public static final int CAPSULE_SLOT = 0;
    public static final int MOTOR_SLOT = 1;
    public static final int BATTERY_SLOT = 2;
    public static final int SLOT_COUNT = 3;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_BATCH = 5;
    public static final int DATA_NEARBY = 6;
    public static final int DATA_SPAWNED = 7;
    public static final int DATA_COUNT = 8;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_SPAWNED = "Spawned";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final DuplicatorInventory inventory = new DuplicatorInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            TrapWave2Logic.DUPLICATOR_BASE_CAPACITY, TrapWave2Logic.DUPLICATOR_MAX_RECEIVE);

    private int progress;
    private int nearbyCount;
    private int spawnedTotal;
    private int activeGlowTicks;
    private int dupState = TrapWave2Logic.DUP_STATE_NO_CAPSULE;
    @Nullable
    private AABB cachedCrowdBox;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATE -> dupState;
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> currentInterval();
                case DATA_BATCH -> TrapWave2Logic.duplicatorBatchCount(getMotorCount());
                case DATA_NEARBY -> nearbyCount;
                case DATA_SPAWNED -> spawnedTotal;
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

    public MobDuplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOB_DUPLICATOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MobDuplicatorBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int motors = getMotorCount();
        int interval = currentInterval();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int spawnCost = TrapWave2Logic.duplicatorSpawnEnergy(getBatteryCount(), ownerLvl);

        ItemStack capsule = inventory.getStackInSlot(CAPSULE_SLOT);
        ResourceLocation entityId = MobCapsuleItem.getCapturedEntityId(capsule);
        if (entityId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
            dupState = TrapWave2Logic.DUP_STATE_NO_CAPSULE;
            progress = 0;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (energy.getEnergyStored() < spawnCost) {
            dupState = TrapWave2Logic.DUP_STATE_NO_ENERGY;
            progress = 0;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (activeGlowTicks > 0) {
            activeGlowTicks--;
        }

        // Update crowd count every 10 ticks (coordinate-staggered to avoid synchronized spikes) or right before spawn
        if (Math.floorMod(level.getGameTime() + worldPosition.hashCode(), 10L) == 0L || progress + 1 >= interval) {
            nearbyCount = countNearbyMobs(level);
        }
        if (TrapWave2Logic.isDuplicatorCrowded(nearbyCount)) {
            dupState = TrapWave2Logic.DUP_STATE_CROWDED;
            progress = 0;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        dupState = TrapWave2Logic.DUP_STATE_WORKING;
        progress++;
        if (progress < interval) {
            updateLit(level, state, true);
            return;
        }
        progress = 0;

        nearbyCount = countNearbyMobs(level);
        int room = TrapWave2Logic.duplicatorSpawnRoom(
                nearbyCount, TrapWave2Logic.duplicatorBatchCount(motors));
        if (room <= 0) {
            dupState = TrapWave2Logic.DUP_STATE_CROWDED;
            updateLit(level, state, false);
            return;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        List<BlockPos> candidates = collectCandidatePositions();
        Collections.shuffle(candidates);

        int spawnedNow = 0;
        for (int n = 0; n < room && !candidates.isEmpty(); n++) {
            Entity raw = type.create(level);
            if (!(raw instanceof Mob mob)) {
                break;
            }
            BlockPos chosen = findFreeSpot(level, mob, candidates);
            if (chosen == null) {
                mob.discard();
                break;
            }
            mob.moveTo(chosen.getX() + 0.5D, chosen.getY(), chosen.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(chosen), MobSpawnType.SPAWNER, null);
            if (level.addFreshEntity(mob)) {
                spawnedNow++;
                spawnedTotal++;
            } else {
                mob.discard();
            }
        }

        if (spawnedNow > 0) {
            energy.consume(spawnCost);
            nearbyCount += spawnedNow;
            activeGlowTicks = 25;
            dupState = TrapWave2Logic.DUP_STATE_WORKING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            level.blockEntityChanged(worldPosition);
        }
    }

    private int countNearbyMobs(ServerLevel level) {
        AABB crowdBox = cachedCrowdBox;
        if (crowdBox == null) {
            int r = TrapWave2Logic.DUPLICATOR_CROWD_HALF_XZ;
            crowdBox = new AABB(
                    worldPosition.getX() - r, worldPosition.getY() - 2, worldPosition.getZ() - r,
                    worldPosition.getX() + r + 1, worldPosition.getY() + 5, worldPosition.getZ() + r + 1);
            cachedCrowdBox = crowdBox;
        }
        return level.getEntitiesOfClass(Mob.class, crowdBox, m -> m.isAlive() && !m.isRemoved()).size();
    }

    private List<BlockPos> collectCandidatePositions() {
        int h = TrapWave2Logic.DUPLICATOR_SPAWN_HALF_XZ;
        List<BlockPos> list = new ArrayList<>();
        for (int dy = -1; dy <= 3; dy++) {
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == 1)) {
                        continue;
                    }
                    list.add(worldPosition.offset(dx, dy, dz));
                }
            }
        }
        return list;
    }

    @Nullable
    private static BlockPos findFreeSpot(ServerLevel level, Mob mob, List<BlockPos> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            BlockPos p = candidates.get(i);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                continue;
            }
            mob.setPos(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
            if (level.noCollision(mob)) {
                candidates.remove(i);
                return p;
            }
        }
        return null;
    }

    public int currentInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return TrapWave2Logic.duplicatorInterval(getMotorCount(), lvl);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(MobDuplicatorBlock.LIT) && state.getValue(MobDuplicatorBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(MobDuplicatorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(TrapWave2Logic.DUPLICATOR_BASE_CAPACITY, getBatteryCount());
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

    public int getDupState() {
        return dupState;
    }

    public int getNearbyCount() {
        return nearbyCount;
    }

    public int getSpawnedTotal() {
        return spawnedTotal;
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    public ItemStackHandler getInventory() {
        return inventory;
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
        return Component.translatable("block.industrialcore.mob_duplicator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new MobDuplicatorMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_SPAWNED, spawnedTotal);
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
        spawnedTotal = Math.max(0, tag.getInt(TAG_SPAWNED));
        workTracker.load(tag);
    }

    private static final class DuplicatorInventory extends ItemStackHandler {
        private final MobDuplicatorBlockEntity owner;

        DuplicatorInventory(MobDuplicatorBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == CAPSULE_SLOT) {
                return stack.getItem() instanceof MobCapsuleItem && MobCapsuleItem.hasCapturedEntity(stack);
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
            if (slot == CAPSULE_SLOT) {
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
}
