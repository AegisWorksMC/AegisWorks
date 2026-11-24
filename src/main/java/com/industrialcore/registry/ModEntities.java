package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;
import com.industrialcore.raid.entity.DimensionalSovereignEntity;
import com.industrialcore.raid.entity.PhaseJuggernautEntity;
import com.industrialcore.raid.entity.RiftAnchorCrystalEntity;
import com.industrialcore.raid.entity.RiftPlasmaBoltEntity;
import com.industrialcore.raid.entity.RiftStalkerEntity;
import com.industrialcore.raid.entity.VoidSentinelEntity;
import com.industrialcore.weapon.EnergyArrowEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Phase 101 / 116: Entity types registered by AegisWorks ({@code industrialcore}).
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, IndustrialCore.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<EnergyArrowEntity>> ENERGY_ARROW =
            ENTITIES.register("energy_arrow",
                    () -> EntityType.Builder.<EnergyArrowEntity>of(EnergyArrowEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(IndustrialCore.MOD_ID + ":energy_arrow"));

    /** Phase 116: Original Dimensional Rift Raid Enemy #1 — Rift Stalker (次元裂傷体). */
    public static final DeferredHolder<EntityType<?>, EntityType<RiftStalkerEntity>> RIFT_STALKER =
            ENTITIES.register("rift_stalker",
                    () -> EntityType.Builder.of(RiftStalkerEntity::new, MobCategory.MONSTER)
                            .sized(0.85F, 1.85F)
                            .fireImmune()
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(IndustrialCore.MOD_ID + ":rift_stalker"));

    /** Phase 116: Original Dimensional Rift Raid Enemy #2 — Void Sentinel (虚空砲撃機). */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidSentinelEntity>> VOID_SENTINEL =
            ENTITIES.register("void_sentinel",
                    () -> EntityType.Builder.of(VoidSentinelEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 1.70F)
                            .fireImmune()
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(IndustrialCore.MOD_ID + ":void_sentinel"));

    /** Phase 116: Original Dimensional Rift Raid Enemy #3 — Phase Juggernaut (異相重装兵). */
    public static final DeferredHolder<EntityType<?>, EntityType<PhaseJuggernautEntity>> PHASE_JUGGERNAUT =
            ENTITIES.register("phase_juggernaut",
                    () -> EntityType.Builder.of(PhaseJuggernautEntity::new, MobCategory.MONSTER)
                            .sized(1.25F, 2.35F)
                            .fireImmune()
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(IndustrialCore.MOD_ID + ":phase_juggernaut"));

    /** Phase 116: Original Dimensional Rift Raid Boss #4 — Dimensional Sovereign (次元支配者). */
    public static final DeferredHolder<EntityType<?>, EntityType<DimensionalSovereignEntity>> DIMENSIONAL_SOVEREIGN =
            ENTITIES.register("dimensional_sovereign",
                    () -> EntityType.Builder.of(DimensionalSovereignEntity::new, MobCategory.MONSTER)
                            .sized(1.75F, 3.20F)
                            .fireImmune()
                            .clientTrackingRange(96)
                            .updateInterval(2)
                            .build(IndustrialCore.MOD_ID + ":dimensional_sovereign"));

    /** Phase 116: Non-destructive purple/crimson plasma bolt fired by Void Sentinel &amp; Dimensional Sovereign. */
    public static final DeferredHolder<EntityType<?>, EntityType<RiftPlasmaBoltEntity>> RIFT_PLASMA_BOLT =
            ENTITIES.register("rift_plasma_bolt",
                    () -> EntityType.Builder.<RiftPlasmaBoltEntity>of(RiftPlasmaBoltEntity::new, MobCategory.MISC)
                            .sized(0.45F, 0.45F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(IndustrialCore.MOD_ID + ":rift_plasma_bolt"));

    /** Phase 120: Floating Dimensional Anchor Crystal powering the Dimensional Sovereign's Absolute Phase Barrier. */
    public static final DeferredHolder<EntityType<?>, EntityType<RiftAnchorCrystalEntity>> RIFT_ANCHOR_CRYSTAL =
            ENTITIES.register("rift_anchor_crystal",
                    () -> EntityType.Builder.of(RiftAnchorCrystalEntity::new, MobCategory.MONSTER)
                            .sized(1.15F, 2.25F)
                            .fireImmune()
                            .clientTrackingRange(96)
                            .updateInterval(2)
                            .build(IndustrialCore.MOD_ID + ":rift_anchor_crystal"));

    private ModEntities() {}
}
