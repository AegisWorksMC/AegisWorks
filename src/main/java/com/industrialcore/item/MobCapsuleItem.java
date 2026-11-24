package com.industrialcore.item;

import com.industrialcore.registry.ModSounds;
import com.industrialcore.trap.TrapWave2Logic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Phase 93: Mob Capture Capsule ({@code mob_capsule}) used to sample mob DNA for the Mob Duplicator.
 *
 * <p>Right-clicking a valid living {@link Mob} records its {@code EntityType} ID into
 * {@link DataComponents#CUSTOM_DATA} and safely removes the source mob. Bosses and players are
 * rejected with a clear action-bar warning and tactile lock sound.</p>
 */
public class MobCapsuleItem extends Item {
    public static final String TAG_ENTITY_ID = "CapturedEntity";
    public static final String TAG_ENTITY_NAME_KEY = "CapturedNameKey";

    public MobCapsuleItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        return tryCapture(stack, player, hand, target);
    }

    /**
     * Attempts to capture {@code target}'s EntityType DNA into {@code stack} (mutating the held
     * stack in {@code hand} when on the server).
     */
    public static InteractionResult tryCapture(ItemStack stack, Player player,
                                               InteractionHand hand, LivingEntity target) {
        if (player.level().isClientSide) {
            return (!hasCapturedEntity(stack) && target instanceof Mob)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        ItemStack activeStack = player.getItemInHand(hand);
        if (activeStack.isEmpty()) {
            activeStack = stack;
        }
        if (hasCapturedEntity(activeStack)) {
            player.displayClientMessage(
                    Component.translatable("message.industrialcore.mob_capsule.already_filled")
                            .withStyle(ChatFormatting.YELLOW), true);
            playFeedback(player, ModSounds.UI_LOCKED.get(), 0.55f, 1.00f);
            return InteractionResult.FAIL;
        }
        if (!(target instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
            return InteractionResult.PASS;
        }

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        boolean disallowedTag = mob.getType().is(Tags.EntityTypes.BOSSES)
                || mob.getType().is(Tags.EntityTypes.CAPTURING_NOT_SUPPORTED);
        if (!TrapWave2Logic.canCaptureEntity(entityId.toString(), target instanceof Player, disallowedTag)) {
            player.displayClientMessage(
                    Component.translatable("message.industrialcore.mob_capsule.blocked",
                                    mob.getType().getDescription())
                            .withStyle(ChatFormatting.RED), true);
            playFeedback(player, ModSounds.UI_LOCKED.get(), 0.55f, 1.00f);
            return InteractionResult.FAIL;
        }

        String descKey = mob.getType().getDescriptionId();
        setCapturedEntity(activeStack, entityId, descKey);
        if (activeStack != stack) {
            setCapturedEntity(stack, entityId, descKey);
        }

        if (mob.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL,
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(),
                    16, 0.35D, 0.35D, 0.35D, 0.12D);
        }
        mob.discard();
        playFeedback(player, ModSounds.MACHINE_COMPLETE.get(), 0.55f, 1.15f);
        player.displayClientMessage(
                Component.translatable("message.industrialcore.mob_capsule.captured",
                                mob.getType().getDescription())
                        .withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    private static void playFeedback(Player player, net.minecraft.sounds.SoundEvent sound,
                                     float vol, float pitch) {
        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    sound, SoundSource.PLAYERS, vol, pitch);
        }
    }

    public static boolean hasCapturedEntity(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(TAG_ENTITY_ID)
                && !data.copyTag().getString(TAG_ENTITY_ID).isBlank();
    }

    @Nullable
    public static ResourceLocation getCapturedEntityId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG_ENTITY_ID)) {
            return null;
        }
        String raw = data.copyTag().getString(TAG_ENTITY_ID);
        return raw.isBlank() ? null : ResourceLocation.tryParse(raw);
    }

    public static Component getCapturedDisplayName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains(TAG_ENTITY_NAME_KEY)) {
            String key = data.copyTag().getString(TAG_ENTITY_NAME_KEY);
            if (!key.isBlank()) {
                return Component.translatable(key);
            }
        }
        ResourceLocation id = getCapturedEntityId(stack);
        return id != null ? Component.literal(id.toString()) : Component.literal("-");
    }

    public static void setCapturedEntity(ItemStack stack, ResourceLocation entityId, String descKey) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_ENTITY_ID, entityId.toString());
            if (descKey != null && !descKey.isBlank()) {
                tag.putString(TAG_ENTITY_NAME_KEY, descKey);
            }
        });
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasCapturedEntity(stack) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (hasCapturedEntity(stack)) {
            return Component.translatable("item.industrialcore.mob_capsule.filled",
                    getCapturedDisplayName(stack));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        if (hasCapturedEntity(stack)) {
            tooltip.add(Component.translatable("gui.industrialcore.tooltip.mob_capsule.filled",
                            getCapturedDisplayName(stack))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            ResourceLocation id = getCapturedEntityId(stack);
            if (id != null) {
                tooltip.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("gui.industrialcore.tooltip.mob_capsule.use_duplicator")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("gui.industrialcore.tooltip.mob_capsule.empty")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
