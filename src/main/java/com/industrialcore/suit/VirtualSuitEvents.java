package com.industrialcore.suit;

import com.industrialcore.IndustrialCore;
import com.industrialcore.item.VirtualSuitArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 98: Server-side Virtual Aegis suit runtime — FE damage absorption, fall negation,
 * high-jump boost, safe survival creative-flight management, attribute modifiers, vision,
 * auto-feed, and shield debuff cleansing.
 */
public final class VirtualSuitEvents {
    public static final ResourceLocation SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "virtual_suit_speed");
    public static final ResourceLocation STEP_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "virtual_suit_step");

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Tracks players whose {@code mayfly} ability was granted by Virtual Aegis flight module. */
    private static final Set<UUID> FLIGHT_GRANTED = ConcurrentHashMap.newKeySet();

    private VirtualSuitEvents() {}

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float incoming = event.getNewDamage();
        if (incoming <= 0.0F) {
            return;
        }

        ItemStack[] stacks = new ItemStack[4];
        int[] energies = new int[4];
        int wornCount = 0;
        for (int i = 0; i < 4; i++) {
            stacks[i] = player.getItemBySlot(ARMOR_SLOTS[i]);
            if (stacks[i].getItem() instanceof VirtualSuitArmorItem) {
                wornCount++;
                energies[i] = VirtualSuitArmorItem.getEnergy(stacks[i]);
            }
        }
        if (wornCount == 0) {
            return;
        }

        boolean fullSet = (wornCount == 4);
        boolean shieldActive = energies[1] > 0
                && VirtualSuitArmorItem.isModuleEnabled(stacks[1], SuitModule.SHIELD);

        VirtualSuitLogic.DamageAbsorbResult res = VirtualSuitLogic.absorbDamage(
                incoming, energies, shieldActive, fullSet);

        for (int i = 0; i < 4; i++) {
            if (res.energyConsumed()[i] > 0) {
                VirtualSuitArmorItem.drainUpTo(stacks[i], res.energyConsumed()[i]);
            }
        }
        event.setNewDamage(res.remainingDamage());
    }

    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        float dist = event.getDistance();
        if (dist <= 3.0F) {
            return;
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (!(boots.getItem() instanceof VirtualSuitArmorItem)
                || !VirtualSuitArmorItem.isModuleEnabled(boots, SuitModule.NOFALL)) {
            return;
        }
        int cost = VirtualSuitLogic.fallNegationCost(dist);
        if (VirtualSuitArmorItem.consumeEnergy(boots, cost)) {
            event.setDistance(0.0F);
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);
        }
    }

    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isShiftKeyDown()) {
            return;
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (!(boots.getItem() instanceof VirtualSuitArmorItem)
                || !VirtualSuitArmorItem.isModuleEnabled(boots, SuitModule.JUMP)) {
            return;
        }
        if (VirtualSuitArmorItem.getEnergy(boots) < VirtualSuitLogic.JUMP_COST) {
            return;
        }
        if (!player.level().isClientSide) {
            VirtualSuitArmorItem.consumeEnergy(boots, VirtualSuitLogic.JUMP_COST);
        }
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.x, dm.y + 0.24D, dm.z);
        player.hurtMarked = true;
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !(player instanceof ServerPlayer sp)) {
            return;
        }

        ItemStack helm = sp.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = sp.getItemBySlot(EquipmentSlot.LEGS);

        boolean periodic = (sp.tickCount % 20 == 0);

        // --- 1. Creative Flight (mod_flight) with strict survival restoration ---
        updateFlight(sp, chest);

        // --- 2. Speed & Step Assist attributes (mod_speed, mod_step) ---
        boolean legsPowered = legs.getItem() instanceof VirtualSuitArmorItem
                && VirtualSuitArmorItem.getEnergy(legs) > 0;
        boolean speedOn = legsPowered && VirtualSuitArmorItem.isModuleEnabled(legs, SuitModule.SPEED);
        boolean stepOn = legsPowered && VirtualSuitArmorItem.isModuleEnabled(legs, SuitModule.STEP);

        syncAttribute(sp.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID,
                0.35D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, speedOn);
        syncAttribute(sp.getAttribute(Attributes.STEP_HEIGHT), STEP_MODIFIER_ID,
                0.6D, AttributeModifier.Operation.ADD_VALUE, stepOn);

        if (periodic) {
            if (speedOn && sp.getDeltaMovement().horizontalDistanceSqr() > 0.0025D) {
                VirtualSuitArmorItem.drainUpTo(legs, VirtualSuitLogic.SPEED_COST_PER_20T);
            }
            if (stepOn) {
                VirtualSuitArmorItem.drainUpTo(legs, VirtualSuitLogic.STEP_COST_PER_20T);
            }
        }

        // --- 3. Helmet: Vision (Night Vision + Water Breathing) & Auto-feed ---
        if (periodic && helm.getItem() instanceof VirtualSuitArmorItem) {
            if (VirtualSuitArmorItem.isModuleEnabled(helm, SuitModule.VISION)
                    && VirtualSuitArmorItem.consumeEnergy(helm, VirtualSuitLogic.VISION_COST_PER_20T)) {
                sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
                sp.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, false));
            }
            if (VirtualSuitArmorItem.isModuleEnabled(helm, SuitModule.AUTOFEED)
                    && (sp.getFoodData().needsFood() || sp.getFoodData().getSaturationLevel() < 4.0F)
                    && VirtualSuitArmorItem.consumeEnergy(helm, VirtualSuitLogic.AUTOFEED_COST)) {
                sp.getFoodData().eat(4, 4.0F);
            }
        }

        // --- 4. Chest: Shield harmful debuff & fire cleansing ---
        if (periodic && chest.getItem() instanceof VirtualSuitArmorItem
                && VirtualSuitArmorItem.isModuleEnabled(chest, SuitModule.SHIELD)
                && VirtualSuitArmorItem.getEnergy(chest) >= VirtualSuitLogic.SHIELD_CLEANSE_COST) {
            boolean cleansed = false;
            if (sp.isOnFire()) {
                sp.clearFire();
                cleansed = true;
            }
            List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> toRemove = new ArrayList<>();
            for (MobEffectInstance inst : sp.getActiveEffects()) {
                if (!inst.getEffect().value().isBeneficial()) {
                    toRemove.add(inst.getEffect());
                }
            }
            for (var eff : toRemove) {
                if (sp.removeEffect(eff)) {
                    cleansed = true;
                }
            }
            if (cleansed) {
                VirtualSuitArmorItem.consumeEnergy(chest, VirtualSuitLogic.SHIELD_CLEANSE_COST);
            }
        }

        // --- 5. Phase 100: Periodic 10-tick Squad Sync for full-set Virtual Aegis wearers ---
        if (sp.tickCount % 10 == 0 && VirtualSuitArmorItem.isFullSetEquipped(sp)) {
            syncNearbySquad(sp);
        }
    }

    private static void syncNearbySquad(ServerPlayer sp) {
        String dimId = sp.serverLevel().dimension().location().toString();
        List<ServerPlayer> levelPlayers = sp.serverLevel().players();
        List<SquadLogic.SquadCandidate> candidates = new ArrayList<>(Math.max(0, levelPlayers.size() - 1));
        for (ServerPlayer other : levelPlayers) {
            if (other == sp || other.isSpectator() || !other.isAlive()) {
                continue;
            }
            boolean otherSuit = VirtualSuitArmorItem.totalSuitCapacity(other) > 0L;
            candidates.add(new SquadLogic.SquadCandidate(
                    other.getUUID(),
                    other.getGameProfile().getName(),
                    dimId,
                    other.getX(), other.getY(), other.getZ(),
                    other.getHealth(), other.getMaxHealth(),
                    otherSuit,
                    otherSuit ? VirtualSuitArmorItem.totalSuitEnergy(other) : 0L,
                    otherSuit ? VirtualSuitArmorItem.totalSuitCapacity(other) : 0L));
        }
        List<SquadLogic.SquadMember> selected = SquadLogic.selectNearbySquad(
                sp.getUUID(), dimId, sp.getX(), sp.getY(), sp.getZ(), candidates);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                sp, new com.industrialcore.network.SquadStatusSyncPayload(dimId, selected));
    }

    private static void updateFlight(ServerPlayer sp, ItemStack chest) {
        UUID id = sp.getUUID();
        if (sp.isCreative() || sp.isSpectator()) {
            FLIGHT_GRANTED.remove(id);
            return;
        }
        boolean canFly = chest.getItem() instanceof VirtualSuitArmorItem
                && VirtualSuitArmorItem.isModuleEnabled(chest, SuitModule.FLIGHT)
                && VirtualSuitArmorItem.getEnergy(chest) >= VirtualSuitLogic.FLIGHT_COST_PER_TICK;

        if (canFly) {
            boolean dirty = false;
            if (!sp.getAbilities().mayfly) {
                sp.getAbilities().mayfly = true;
                dirty = true;
            }
            FLIGHT_GRANTED.add(id);
            if (sp.getAbilities().flying) {
                VirtualSuitArmorItem.drainUpTo(chest, VirtualSuitLogic.FLIGHT_COST_PER_TICK);
                if (VirtualSuitArmorItem.getEnergy(chest) < VirtualSuitLogic.FLIGHT_COST_PER_TICK) {
                    sp.getAbilities().flying = false;
                    sp.getAbilities().mayfly = false;
                    FLIGHT_GRANTED.remove(id);
                    dirty = true;
                }
            }
            if (dirty) {
                sp.onUpdateAbilities();
            }
        } else if (FLIGHT_GRANTED.remove(id)) {
            if (sp.getAbilities().mayfly || sp.getAbilities().flying) {
                sp.getAbilities().flying = false;
                sp.getAbilities().mayfly = false;
                sp.onUpdateAbilities();
            }
        }
    }

    private static void syncAttribute(AttributeInstance inst, ResourceLocation id,
                                      double amount, AttributeModifier.Operation op, boolean enable) {
        if (inst == null) {
            return;
        }
        boolean has = inst.hasModifier(id);
        if (enable && !has) {
            inst.addTransientModifier(new AttributeModifier(id, amount, op));
        } else if (!enable && has) {
            inst.removeModifier(id);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (FLIGHT_GRANTED.remove(sp.getUUID()) && !sp.isCreative() && !sp.isSpectator()) {
                sp.getAbilities().flying = false;
                sp.getAbilities().mayfly = false;
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        FLIGHT_GRANTED.clear();
    }
}
