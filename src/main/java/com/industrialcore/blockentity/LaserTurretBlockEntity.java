package com.industrialcore.blockentity;

import com.industrialcore.block.LaserTurretBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineLevelMath;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.IUpgradeableMachine;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.machine.VirtualUpgradeBank;
import com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory;
import com.industrialcore.menu.LaserTurretMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModSounds;
import com.industrialcore.trap.TrapWave2Logic;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Phase 93 / 113: BlockEntity for the Defense Laser Turret ({@code laser_turret}).
 *
 * <p>Phase 113 upgrades:
 * <ul>
 *   <li>Always-on standby vigilance ({@code LIT=true} whenever powered and not redstone-disabled).</li>
 *   <li>Real-time client target coordinate sync ({@code HasTarget}, {@code Tx/Ty/Tz}, {@code PulseTicks})
 *       for smooth 3D turret yaw/pitch tracking &amp; client-rendered 3D pulse laser beams.</li>
 *   <li>Rapid pulse-laser firing (base 8 ticks, accelerated by motors) with Specialization Module
 *       scaling for detection range &amp; pulse damage.</li>
 * </ul>
 */
public class LaserTurretBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, IUpgradeableMachine {
    public static final String MACHINE_ID = "laser_turret";

    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_RANGE = 5;
    public static final int DATA_DAMAGE_X10 = 6;
    public static final int DATA_KILLS = 7;
    public static final int DATA_SHOT_COST = 8;
    public static final int DATA_COUNT = 9;

    private static final GameProfile TURRET_PROFILE = new GameProfile(
            UUID.fromString("91a3c5e7-22b4-4e81-a6c9-33f8d12047b2"), "[IndustrialCore_Turret]");

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_KILLS = "Kills";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_HAS_TARGET = "HasTarget";
    private static final String TAG_TX = "Tx";
    private static final String TAG_TY = "Ty";
    private static final String TAG_TZ = "Tz";
    private static final String TAG_PULSE_TICKS = "PulseTicks";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final VirtualUpgradeBank upgradeBank = new VirtualUpgradeBank();
    private final TurretInventory inventory = new TurretInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            TrapWave2Logic.TURRET_BASE_CAPACITY, TrapWave2Logic.TURRET_MAX_RECEIVE);

    private int progress;
    private int killsTotal;
    private int lockedEntityId = -1;
    private int scanTicks = 0;
    private MachineStatus status = MachineStatus.IDLE;

    // Real-time synced turret aim & 3D pulse beam state
    private boolean hasTarget;
    private double targetX;
    private double targetY;
    private double targetZ;
    private int firingPulseTicks;
    private long clientPulseStartMs;
    private boolean lastSyncedHasTarget;
    private double lastSyncedTx;
    private double lastSyncedTy;
    private double lastSyncedTz;
    private int lastSyncedPulse;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> currentFireInterval();
                case DATA_RANGE -> TrapWave2Logic.turretRange(getSpecialCount());
                case DATA_DAMAGE_X10 -> Math.round(TrapWave2Logic.turretDamage(getSpecialCount()) * 10.0F);
                case DATA_KILLS -> killsTotal;
                case DATA_SHOT_COST -> currentShotCost();
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

    public LaserTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_TURRET.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserTurretBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int interval = currentFireInterval();
        int shotCost = currentShotCost();

        if (level.hasNeighborSignal(worldPosition)) {
            status = MachineStatus.IDLE;
            progress = 0;
            clearTargetAndSync(level, state, false);
            return;
        }

        if (energy.getEnergyStored() < shotCost) {
            status = MachineStatus.NO_ENERGY;
            progress = 0;
            clearTargetAndSync(level, state, false);
            return;
        }

        // Always-on standby vigilance whenever powered and not redstone-disabled
        updateLit(level, state, true);

        if (firingPulseTicks > 0) {
            firingPulseTicks--;
        }

        int specials = getSpecialCount();
        int range = com.industrialcore.Config.turretBaseRange()
                + com.industrialcore.machine.UpgradeMath.turretRangeBonus(specials);
        Vec3 origin = new Vec3(worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.78D,
                worldPosition.getZ() + 0.5D);

        // Keep tracking existing target every tick; re-scan every 4 ticks if none locked
        LivingEntity locked = resolveLockedTarget(level, origin, specials);
        if (locked == null && (++scanTicks >= 4 || progress >= interval - 1)) {
            scanTicks = 0;
            locked = acquireNewTarget(level, origin, range, specials);
            lockedEntityId = locked != null ? locked.getId() : -1;
        }

        if (locked == null) {
            hasTarget = false;
            status = MachineStatus.IDLE;
            progress = Math.min(progress + 1, interval);
            syncClientAimIfChanged(level, state);
            return;
        }

        Vec3 impactPoint = new Vec3(locked.getX(),
                locked.getY() + locked.getBbHeight() * 0.55D,
                locked.getZ());
        hasTarget = true;
        targetX = impactPoint.x;
        targetY = impactPoint.y;
        targetZ = impactPoint.z;
        status = MachineStatus.WORKING;

        progress++;
        if (progress >= interval) {
            progress = 0;
            FakePlayer fakePlayer = FakePlayerFactory.get(level, TURRET_PROFILE);
            fakePlayer.setPos(origin.x, origin.y, origin.z);
            float damage = (float) (TrapWave2Logic.turretDamage(specials)
                    * com.industrialcore.Config.turretDamageMultiplier());
            boolean hurt = locked.hurt(level.damageSources().playerAttack(fakePlayer), damage);
            if (hurt) {
                energy.consume(shotCost);
                if (!locked.isAlive()) {
                    killsTotal++;
                    lockedEntityId = -1;
                    if (workTracker.ownerUuid() != null) {
                        var owner = level.getServer().getPlayerList().getPlayer(workTracker.ownerUuid());
                        com.industrialcore.level.AegisAdvancements.grant(
                                owner, com.industrialcore.level.AegisAdvancements.TURRET_FIRST_KILL);
                    }
                    com.industrialcore.level.AegisAdvancements.grantNearby(
                            level, worldPosition, 24.0D,
                            com.industrialcore.level.AegisAdvancements.TURRET_FIRST_KILL);
                }
                firingPulseTicks = 5;
                workTracker.recordActiveTick(level, MACHINE_ID);
                setChanged();
                // Phase 116: Unify beam rendering, impact sparks, and firing SE with Pulse Laser weapon!
                Vec3 dir = impactPoint.subtract(origin);
                double len = dir.length();
                Vec3 muzzle = len > 1.0E-4D ? origin.add(dir.scale(0.52D / len)) : origin;
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingChunk(
                        level, new net.minecraft.world.level.ChunkPos(worldPosition),
                        new com.industrialcore.network.WeaponBeamFxPayload(
                                com.industrialcore.network.WeaponBeamFxPayload.KIND_LASER_BEAM,
                                muzzle.x, muzzle.y, muzzle.z,
                                impactPoint.x, impactPoint.y, impactPoint.z,
                                0xFF3EF7FF, 5, 0.082F, 0.35F, true));
                level.playSound(null, worldPosition, ModSounds.WEAPON_LASER_BEAM.get(),
                        SoundSource.BLOCKS, 0.34F, 1.06F + level.random.nextFloat() * 0.10F);
                level.playSound(null, BlockPos.containing(impactPoint), ModSounds.WEAPON_LASER_HIT_SIZZLE.get(),
                        SoundSource.BLOCKS, 0.20F, 1.12F);
                forceSyncClientAim(level, state);
                return;
            }
        }

        syncClientAimIfChanged(level, state);
    }

    @Nullable
    private LivingEntity resolveLockedTarget(ServerLevel level, Vec3 origin, int specials) {
        if (lockedEntityId < 0) {
            return null;
        }
        Entity ent = level.getEntity(lockedEntityId);
        if (!(ent instanceof LivingEntity living) || !isValidTarget(living)) {
            lockedEntityId = -1;
            return null;
        }
        if (!TrapWave2Logic.isInTurretRange(living.distanceToSqr(origin), specials)) {
            lockedEntityId = -1;
            return null;
        }
        Vec3 center = new Vec3(living.getX(), living.getY() + living.getBbHeight() * 0.55D, living.getZ());
        if (!hasLineOfSight(level, origin, center)) {
            lockedEntityId = -1;
            return null;
        }
        return living;
    }

    @Nullable
    private LivingEntity acquireNewTarget(ServerLevel level, Vec3 origin, int range, int specials) {
        AABB box = new AABB(origin.x - range, origin.y - range, origin.z - range,
                origin.x + range, origin.y + range, origin.z + range);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> isValidTarget(e) && TrapWave2Logic.isInTurretRange(e.distanceToSqr(origin), specials));
        candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
        for (LivingEntity candidate : candidates) {
            Vec3 center = new Vec3(candidate.getX(),
                    candidate.getY() + candidate.getBbHeight() * 0.55D,
                    candidate.getZ());
            if (hasLineOfSight(level, origin, center)) {
                return candidate;
            }
        }
        return null;
    }

    private void clearTargetAndSync(ServerLevel level, BlockState state, boolean lit) {
        hasTarget = false;
        lockedEntityId = -1;
        firingPulseTicks = 0;
        updateLit(level, state, lit);
        syncClientAimIfChanged(level, state);
    }

    private void syncClientAimIfChanged(ServerLevel level, BlockState state) {
        boolean changed = (hasTarget != lastSyncedHasTarget)
                || ((firingPulseTicks > 0) != (lastSyncedPulse > 0));
        if (!changed && hasTarget) {
            double dx = targetX - lastSyncedTx;
            double dy = targetY - lastSyncedTy;
            double dz = targetZ - lastSyncedTz;
            changed = (dx * dx + dy * dy + dz * dz) >= 0.0225D; // >= 0.15 blocks
        }
        if (changed) {
            forceSyncClientAim(level, state);
        }
    }

    private void forceSyncClientAim(ServerLevel level, BlockState state) {
        lastSyncedHasTarget = hasTarget;
        lastSyncedTx = targetX;
        lastSyncedTy = targetY;
        lastSyncedTz = targetZ;
        lastSyncedPulse = firingPulseTicks;
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private static boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
        HitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    public static boolean isValidTarget(LivingEntity entity) {
        boolean isEnemy = entity instanceof Enemy;
        boolean isPlayer = entity instanceof Player;
        boolean isVillagerOrGolem = entity instanceof AbstractVillager || entity instanceof AbstractGolem;
        boolean isTamedOrOwned = (entity instanceof TamableAnimal ta && ta.isTame())
                || (entity instanceof AbstractHorse horse && horse.isTamed())
                || (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null);
        boolean isArmorStand = entity instanceof ArmorStand;
        return TrapWave2Logic.canTurretTarget(
                isEnemy, isPlayer, isVillagerOrGolem, isTamedOrOwned, isArmorStand,
                entity.isAlive() && !entity.isInvulnerable());
    }

    public int currentFireInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return TrapWave2Logic.turretFireInterval(getMotorCount(), lvl);
    }

    public int currentShotCost() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return TrapWave2Logic.turretShotEnergy(getBatteryCount(), lvl);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(LaserTurretBlock.LIT) && state.getValue(LaserTurretBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(LaserTurretBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(TrapWave2Logic.TURRET_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    // --- Client-readable real-time tracking & beam getters ---

    public boolean hasClientTarget() {
        return hasTarget;
    }

    public double getClientTargetX() {
        return targetX;
    }

    public double getClientTargetY() {
        return targetY;
    }

    public double getClientTargetZ() {
        return targetZ;
    }

    public boolean isClientFiringPulse() {
        if (firingPulseTicks > 0) {
            return true;
        }
        return hasTarget && (net.minecraft.Util.getMillis() - clientPulseStartMs) < 260L;
    }

    // --- IUpgradeableMachine & Virtual Upgrade Bank ---

    @Override
    public VirtualUpgradeBank getUpgradeBank() {
        return upgradeBank;
    }

    @Override
    public SpecialCategory getSpecialCategory() {
        return SpecialCategory.TURRET;
    }

    @Override
    public String getUpgradeMachineId() {
        return MACHINE_ID;
    }

    @Override
    public int getLegacyUpgradeCount() {
        return inventory.getStackInSlot(MOTOR_SLOT).getCount()
                + inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    @Override
    public int getTotalUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.totalUpgradeLimit(sl, MACHINE_ID);
        }
        return MachineLevelMath.totalUpgradeLimitForLevel(1);
    }

    @Override
    public void onUpgradeBankChanged() {
        syncBatteryCapacity();
        setChanged();
    }

    public int getMotorCount() {
        return upgradeBank.motors() + inventory.getStackInSlot(MOTOR_SLOT).getCount();
    }

    public int getBatteryCount() {
        return upgradeBank.batteries() + inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    public int getSpecialCount() {
        return upgradeBank.specials();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public int getKillsTotal() {
        return killsTotal;
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.laser_turret");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new LaserTurretMenu(containerId, playerInventory, this, dataAccess);
    }

    // --- Client sync packets for real-time tracking & 3D pulse beam ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean(TAG_HAS_TARGET, hasTarget);
        if (hasTarget) {
            tag.putDouble(TAG_TX, targetX);
            tag.putDouble(TAG_TY, targetY);
            tag.putDouble(TAG_TZ, targetZ);
        }
        tag.putInt(TAG_PULSE_TICKS, firingPulseTicks);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        applyClientSyncTag(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
                             ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            applyClientSyncTag(tag);
        }
    }

    private void applyClientSyncTag(CompoundTag tag) {
        this.hasTarget = tag.getBoolean(TAG_HAS_TARGET);
        if (this.hasTarget) {
            this.targetX = tag.getDouble(TAG_TX);
            this.targetY = tag.getDouble(TAG_TY);
            this.targetZ = tag.getDouble(TAG_TZ);
        }
        int pulse = tag.getInt(TAG_PULSE_TICKS);
        this.firingPulseTicks = pulse;
        if (pulse > 0) {
            this.clientPulseStartMs = net.minecraft.Util.getMillis();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_KILLS, killsTotal);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        upgradeBank.save(tag);
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        upgradeBank.load(tag);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        killsTotal = Math.max(0, tag.getInt(TAG_KILLS));
        workTracker.load(tag);
    }

    private static final class TurretInventory extends ItemStackHandler {
        private final LaserTurretBlockEntity owner;

        TurretInventory(LaserTurretBlockEntity owner) {
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
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            return (slot == MOTOR_SLOT || slot == BATTERY_SLOT)
                    ? owner.getUpgradeLimit() : super.getSlotLimit(slot);
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
