package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.item.VirtualSuitArmorItem;
import com.industrialcore.suit.VirtualArmorGlowMath;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Custom armor {@link RenderLayer} for the Virtual Aegis powered suit.
 *
 * <p>Both passes match vanilla {@code armorCutoutNoCull}'s {@code VIEW_OFFSET_Z_LAYERING} and use
 * {@code EQUAL_DEPTH_TEST} so every glowing emissive pixel and every scrolling shimmer wave
 * conforms bit-for-bit to the opaque armor plates with zero depth rejection and zero floating
 * ghost boxes over transparent hat/leg regions:</p>
 * <ol>
 *   <li><b>Full-Bright Emissive Pass</b>: illuminates the V-visor, hexagonal chest energy core,
 *       twin thrusters, spine conduit, and shoulder/forearm/knee channels in total darkness with
 *       a smooth breathing pulse ({@code 0.85..1.0}).</li>
 *   <li><b>Enchantment-Style Neon Shimmer Pass</b>: diagonally scrolls {@code va_neon_glint.png}
 *       in additive blending at restrained intensity ({@code 0.22..0.30}) so the dark-titanium
 *       silhouette stays razor-sharp.</li>
 * </ol>
 */
public class VirtualArmorGlowLayer<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>>
        extends RenderLayer<T, M> {
    public static final ResourceLocation EMISSIVE_LAYER_1 = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/models/armor/va_layer_1_emissive.png");
    public static final ResourceLocation EMISSIVE_LAYER_2 = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/models/armor/va_layer_2_emissive.png");
    public static final ResourceLocation NEON_GLINT_TEX = ResourceLocation.fromNamespaceAndPath(
            IndustrialCore.MOD_ID, "textures/misc/va_neon_glint.png");

    private static final RenderType EMISSIVE_RT_1 = ArmorGlowRenderTypes.armorEmissive(EMISSIVE_LAYER_1);
    private static final RenderType EMISSIVE_RT_2 = ArmorGlowRenderTypes.armorEmissive(EMISSIVE_LAYER_2);

    private final A innerModel;
    private final A outerModel;

    public VirtualArmorGlowLayer(RenderLayerParent<T, M> renderer, A innerModel, A outerModel) {
        super(renderer);
        this.innerModel = innerModel;
        this.outerModel = outerModel;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       T entity, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        renderSlot(poseStack, bufferSource, entity, EquipmentSlot.CHEST, ageInTicks);
        renderSlot(poseStack, bufferSource, entity, EquipmentSlot.LEGS, ageInTicks);
        renderSlot(poseStack, bufferSource, entity, EquipmentSlot.FEET, ageInTicks);
        renderSlot(poseStack, bufferSource, entity, EquipmentSlot.HEAD, ageInTicks);
    }

    private void renderSlot(PoseStack poseStack, MultiBufferSource bufferSource,
                            T entity, EquipmentSlot slot, float ageInTicks) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof VirtualSuitArmorItem armorItem)
                || armorItem.getEquipmentSlot() != slot) {
            return;
        }

        boolean inner = (slot == EquipmentSlot.LEGS);
        A model = inner ? this.innerModel : this.outerModel;
        this.getParentModel().copyPropertiesTo(model);
        setPartVisibility(model, slot);

        // Pass 1: Full-bright breathing emissive visor/core/conduit mask (GL_ONE, GL_ONE -> scale RGB by pulse)
        float pulse = VirtualArmorGlowMath.emissivePulse(ageInTicks);
        int emissiveColor = VirtualArmorGlowMath.packArgb(1.0F, pulse, pulse, pulse);
        VertexConsumer emissiveVc = bufferSource.getBuffer(inner ? EMISSIVE_RT_2 : EMISSIVE_RT_1);
        model.renderToBuffer(poseStack, emissiveVc, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, emissiveColor);

        // Pass 2: Restrained diagonal additive neon-cyan / electric-blue shimmer (0.22..0.30 RGB scale, never washes out dark titanium)
        float u = VirtualArmorGlowMath.shimmerScrollU(ageInTicks);
        float v = VirtualArmorGlowMath.shimmerScrollV(ageInTicks);
        float shimmer = VirtualArmorGlowMath.shimmerIntensity(ageInTicks);
        int shimmerColor = VirtualArmorGlowMath.packArgb(1.0F,
                shimmer * 0.28F, shimmer * 0.85F, shimmer);
        VertexConsumer shimmerVc = bufferSource.getBuffer(
                ArmorGlowRenderTypes.neonShimmer(NEON_GLINT_TEX, u, v));
        model.renderToBuffer(poseStack, shimmerVc, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, shimmerColor);
    }

    private static <A extends HumanoidModel<?>> void setPartVisibility(A model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {}
        }
    }

    /**
     * Custom armor-aligned RenderTypes using {@code VIEW_OFFSET_Z_LAYERING} + {@code EQUAL_DEPTH_TEST}
     * (matching vanilla {@code armorCutoutNoCull} / {@code armorEntityGlint}) so both the full-bright
     * emissive mask and the scrolling neon shimmer adhere strictly to opaque armor pixels.
     */
    private static final class ArmorGlowRenderTypes extends RenderType {
        private ArmorGlowRenderTypes(String name, VertexFormat fmt, VertexFormat.Mode mode, int buf,
                                     boolean crumble, boolean sort, Runnable setup, Runnable clear) {
            super(name, fmt, mode, buf, crumble, sort, setup, clear);
        }

        static RenderType armorEmissive(ResourceLocation texture) {
            return create(
                    "industrialcore_va_emissive",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_EYES_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setDepthTestState(EQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)
            );
        }

        static RenderType neonShimmer(ResourceLocation texture, float u, float v) {
            return create(
                    "industrialcore_va_neon_shimmer",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENERGY_SWIRL_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTexturingState(new OffsetTexturingStateShard(u, v))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setDepthTestState(EQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)
            );
        }
    }
}
