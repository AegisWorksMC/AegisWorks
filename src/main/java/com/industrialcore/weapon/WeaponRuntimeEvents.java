package com.industrialcore.weapon;

import com.industrialcore.blockentity.QuantumStorageBlockEntity.FluidKey;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.ItemKey;
import com.industrialcore.item.ElectricWeaponItem;
import com.industrialcore.item.PortableQuantumTerminalItem;
import com.industrialcore.item.PulseLaserItem;
import com.industrialcore.item.QuantumBladeItem;
import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import com.industrialcore.storage.QuantumNetworkData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Phase 101: Server runtime handlers for Electric Weapons:
 * <ul>
 *   <li>Active {@code att_gravity_arrow} singularity fields (50-tick inward vortex pulling mobs within 7m).</li>
 *   <li>{@code att_quantum_loot} bonus fortune drops + direct collection into linked Portable Quantum
 *       Terminal Quantum Storage channel or player inventory.</li>
 *   <li>{@code att_plasma_wave} trigger when attacking a mob with {@code quantum_blade}.</li>
 * </ul>
 */
public final class WeaponRuntimeEvents {
    private static final class ActiveGravityField {
        final ResourceKey<Level> dimension;
        final Vec3 center;
        @Nullable final UUID ownerUuid;
        int ticksRemaining;

        ActiveGravityField(ResourceKey<Level> dimension, Vec3 center, @Nullable UUID ownerUuid, int ticks) {
            this.dimension = dimension;
            this.center = center;
            this.ownerUuid = ownerUuid;
            this.ticksRemaining = ticks;
        }
    }

    private static final class ActiveSwiftDash {
        final ResourceKey<Level> dimension;
        final UUID playerUuid;
        final Vec3 origin;
        final Vec3 dest;
        final java.util.Set<Integer> slicedEntityIds = new java.util.HashSet<>();
        int tick;
        final int totalTicks;

        ActiveSwiftDash(ResourceKey<Level> dimension, UUID playerUuid, Vec3 origin, Vec3 dest, int totalTicks) {
            this.dimension = dimension;
            this.playerUuid = playerUuid;
            this.origin = origin;
            this.dest = dest;
            this.totalTicks = Math.max(1, totalTicks);
            this.tick = 0;
        }
    }

    private static final List<ActiveGravityField> GRAVITY_FIELDS = new ArrayList<>();
    private static final List<ActiveSwiftDash> SWIFT_DASHES = new ArrayList<>();

    private WeaponRuntimeEvents() {}

    /** Starts a high-speed 6-tick Phase Dash forward piercing dash. */
    public static void startSwiftDash(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 dest) {
        SWIFT_DASHES.removeIf(d -> d.playerUuid.equals(player.getUUID()));
        ActiveSwiftDash dash = new ActiveSwiftDash(
                level.dimension(), player.getUUID(), origin, dest, VirtualWeaponLogic.BLADE_SWIFT_DASH_TICKS);
        SWIFT_DASHES.add(dash);
        // Immediately perform tick 1 slice step so point-blank targets are cut on the opening frame
        stepSwiftDash(level, dash);
    }

    private static boolean stepSwiftDash(ServerLevel sl, ActiveSwiftDash dash) {
        Entity e = sl.getEntity(dash.playerUuid);
        if (!(e instanceof ServerPlayer sp) || !sp.isAlive()) {
            return true;
        }
        int prevTick = dash.tick;
        dash.tick++;
        double t0 = (double) prevTick / (double) dash.totalTicks;
        double t1 = Math.min(1.0D, (double) dash.tick / (double) dash.totalTicks);
        Vec3 segStart = dash.origin.lerp(dash.dest, t0);
        Vec3 segEnd = dash.origin.lerp(dash.dest, t1);

        sp.teleportTo(sl, segEnd.x, segEnd.y, segEnd.z, sp.getYRot(), sp.getXRot());
        Vec3 stepVel = segEnd.subtract(segStart);
        sp.setDeltaMovement(dash.tick < dash.totalTicks ? stepVel.scale(0.65D) : stepVel.scale(0.18D));
        sp.hurtMarked = true;
        sp.fallDistance = 0.0F;

        // Slice EVERY living entity within the 2.5m+ corridor along this dash segment!
        double halfW = VirtualWeaponLogic.BLADE_SWIFT_CORRIDOR_HALF_WIDTH;
        AABB corridorBox = new AABB(segStart, segEnd).inflate(halfW + 1.0D);
        List<LivingEntity> mobs = sl.getEntitiesOfClass(LivingEntity.class, corridorBox,
                m -> m != sp && m.isAlive() && !m.isSpectator() && !dash.slicedEntityIds.contains(m.getId()));
        if (!mobs.isEmpty()) {
            java.util.Map<Integer, LivingEntity> byId = new java.util.HashMap<>();
            List<VirtualWeaponLogic.TargetCandidate> cands = new ArrayList<>(mobs.size());
            for (LivingEntity m : mobs) {
                byId.put(m.getId(), m);
                cands.add(new VirtualWeaponLogic.TargetCandidate(
                        m.getId(), m.getX(), m.getY() + m.getBbHeight() * 0.5D, m.getZ()));
            }
            List<VirtualWeaponLogic.TargetCandidate> hits = VirtualWeaponLogic.findCorridorTargets(
                    segStart.x, segStart.y + 0.9D, segStart.z,
                    segEnd.x, segEnd.y + 0.9D, segEnd.z,
                    halfW, cands);
            for (VirtualWeaponLogic.TargetCandidate tc : hits) {
                LivingEntity victim = byId.get(tc.id());
                if (victim != null && victim.isAlive() && dash.slicedEntityIds.add(victim.getId())) {
                    victim.invulnerableTime = 0;
                    victim.hurt(sl.damageSources().playerAttack(sp), VirtualWeaponLogic.BLADE_BLINK_SLASH_DAMAGE);
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK, tc.x(), tc.y(), tc.z(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
                    sl.sendParticles(ParticleTypes.CRIT, tc.x(), tc.y(), tc.z(), 8, 0.3D, 0.3D, 0.3D, 0.12D);
                }
            }
        }

        sl.sendParticles(ParticleTypes.END_ROD, segEnd.x, segEnd.y + 0.9D, segEnd.z, 3, 0.15D, 0.25D, 0.15D, 0.02D);
        return dash.tick >= dash.totalTicks;
    }

    public static void spawnGravityField(ServerLevel level, Vec3 center, @Nullable Entity owner) {
        UUID ownerId = owner != null ? owner.getUUID() : null;
        GRAVITY_FIELDS.add(new ActiveGravityField(
                level.dimension(), center, ownerId, VirtualWeaponLogic.GRAVITY_DURATION_TICKS));

        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.85F, 0.55F);

        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 64.0D,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_GRAVITY_FIELD,
                        center.x, center.y, center.z,
                        center.x, center.y, center.z,
                        0xFFB86BFF,
                        VirtualWeaponLogic.GRAVITY_DURATION_TICKS,
                        (float) VirtualWeaponLogic.GRAVITY_RADIUS));
    }

    public static int activeGravityFieldCount() {
        return GRAVITY_FIELDS.size();
    }

    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) {
            return;
        }
        ResourceKey<Level> dim = sl.dimension();

        // 1. Advance active Phase Dash corridors smoothly over 6 ticks
        if (!SWIFT_DASHES.isEmpty()) {
            Iterator<ActiveSwiftDash> dashIt = SWIFT_DASHES.iterator();
            while (dashIt.hasNext()) {
                ActiveSwiftDash dash = dashIt.next();
                if (!dash.dimension.equals(dim)) {
                    continue;
                }
                if (stepSwiftDash(sl, dash)) {
                    dashIt.remove();
                }
            }
        }

        if (GRAVITY_FIELDS.isEmpty()) {
            return;
        }
        Iterator<ActiveGravityField> it = GRAVITY_FIELDS.iterator();
        while (it.hasNext()) {
            ActiveGravityField field = it.next();
            if (!field.dimension.equals(dim)) {
                continue;
            }
            field.ticksRemaining--;
            if (field.ticksRemaining <= 0) {
                it.remove();
                continue;
            }

            double r = VirtualWeaponLogic.GRAVITY_RADIUS;
            AABB box = new AABB(field.center, field.center).inflate(r);
            List<LivingEntity> mobs = sl.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && !e.isSpectator() && !(e instanceof Player));

            for (LivingEntity mob : mobs) {
                VirtualWeaponLogic.Vec3d pull = VirtualWeaponLogic.gravityPullVector(
                        field.center.x, field.center.y, field.center.z,
                        mob.getX(), mob.getY(), mob.getZ(), r);
                if (pull.x() != 0.0D || pull.y() != 0.0D || pull.z() != 0.0D) {
                    Vec3 cur = mob.getDeltaMovement();
                    mob.setDeltaMovement(cur.x * 0.65D + pull.x(), cur.y * 0.65D + pull.y(), cur.z * 0.65D + pull.z());
                    mob.hurtMarked = true;
                }
            }

            if ((field.ticksRemaining & 3) == 0) {
                sl.sendParticles(ParticleTypes.PORTAL,
                        field.center.x, field.center.y + 0.3D, field.center.z,
                        10, 1.4D, 0.5D, 1.4D, 0.08D);
                sl.sendParticles(ParticleTypes.DRAGON_BREATH,
                        field.center.x, field.center.y + 0.25D, field.center.z,
                        4, 0.6D, 0.25D, 0.6D, 0.02D);
            }
        }
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ItemStack main = sp.getMainHandItem();
            if (main.getItem() instanceof QuantumBladeItem blade) {
                blade.performKatanaSwing(sp, main);
            } else if (main.getItem() instanceof PulseLaserItem && sp.isShiftKeyDown()) {
                PulseLaserItem.toggleTargetMode(sp, main);
                event.setCanceled(true);
            }
        }
    }

    /** Phase 115: Applies configured {@code defense_and_weapons.weaponDamageMultiplier} to AegisWorks electric weapons. */
    public static void onLivingDamagePre(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof ServerPlayer sp) {
            ItemStack main = sp.getMainHandItem();
            if (main.getItem() instanceof ElectricWeaponItem) {
                double mult = com.industrialcore.Config.weaponDamageMultiplier();
                if (Math.abs(mult - 1.0D) > 1.0E-6D) {
                    event.setNewDamage((float) Math.max(0.5D, event.getNewDamage() * mult));
                }
            }
        }
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel sl)) {
            return;
        }
        ItemStack weapon = sp.getMainHandItem();
        if (!(weapon.getItem() instanceof QuantumBladeItem)
                || !ElectricWeaponItem.isAttachmentEnabled(weapon, WeaponAttachment.QUANTUM_LOOT)) {
            return;
        }
        if (!ElectricWeaponItem.consumeEnergy(weapon, VirtualWeaponLogic.BLADE_FE_LOOT_BONUS, sp.isCreative())) {
            return;
        }

        // Locate linked Portable Quantum Terminal channel if player carries one
        QuantumNetworkData qNet = QuantumNetworkData.get(sl);
        QuantumChannel<ItemKey, FluidKey> qChannel = findLinkedTerminalChannel(sp, qNet);

        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) {
                continue;
            }
            // Fortune-like +1 bonus for stackable drops (up to max stack size)
            if (stack.getMaxStackSize() > 1 && stack.getCount() < stack.getMaxStackSize()) {
                stack.grow(1);
            }
            // 1. Try direct insert into linked Quantum Storage channel
            if (qChannel != null) {
                ItemKey key = ItemKey.of(stack);
                BigInteger inserted = qChannel.storage().insertItem(key, BigInteger.valueOf(stack.getCount()), false);
                if (inserted.signum() > 0) {
                    qChannel.bumpRevision();
                    qNet.markMutated();
                    int rem = stack.getCount() - inserted.intValue();
                    if (rem <= 0) {
                        it.remove();
                        continue;
                    }
                    stack.setCount(rem);
                }
            }
            // 2. Otherwise insert directly into player inventory
            if (sp.getInventory().add(stack) && stack.isEmpty()) {
                it.remove();
            } else {
                drop.setItem(stack);
            }
        }
    }

    @Nullable
    private static QuantumChannel<ItemKey, FluidKey> findLinkedTerminalChannel(ServerPlayer sp,
                                                                               QuantumNetworkData net) {
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof PortableQuantumTerminalItem) {
                String chId = PortableQuantumTerminalItem.getChannelId(st);
                if (!chId.isEmpty()) {
                    QuantumChannel<ItemKey, FluidKey> ch = net.getChannel(chId);
                    if (ch != null && ch.canAccess(sp.getUUID().toString())) {
                        return ch;
                    }
                }
            }
        }
        return null;
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            ResourceKey<Level> dim = sl.dimension();
            GRAVITY_FIELDS.removeIf(f -> f.dimension.equals(dim));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        GRAVITY_FIELDS.clear();
        SWIFT_DASHES.clear();
    }
}
