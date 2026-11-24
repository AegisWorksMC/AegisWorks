package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;
import com.industrialcore.machine.MythicGear;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * Armour materials of the Phase 24 equipment.
 *
 * <p>Only adamantite has an armour set: the mod's other two gear materials are tools, because the
 * progression gives "durable" (adamantite), "fast" (mithril) and "high performance" (orichalcum)
 * one clear role each. The numbers come from {@link MythicGear}, so the durability, the armour
 * points and the enchantability are unit tested without a game.</p>
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, IndustrialCore.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ADAMANTITE =
            ARMOR_MATERIALS.register("adamantite", () -> {
                MythicGear.ArmorSet set = MythicGear.ADAMANTITE_ARMOR;
                return new ArmorMaterial(
                        Map.of(ArmorItem.Type.HELMET, set.helmet(),
                                ArmorItem.Type.CHESTPLATE, set.chestplate(),
                                ArmorItem.Type.LEGGINGS, set.leggings(),
                                ArmorItem.Type.BOOTS, set.boots()),
                        set.enchantment(),
                        SoundEvents.ARMOR_EQUIP_NETHERITE,
                        () -> Ingredient.of(ModItems.ADAMANTITE_INGOT.get()),
                        // Short layer name: the texture lives at textures/models/armor/ad_layer_1.png,
                        // which keeps the path inside the Windows 260 character limit.
                        List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(
                                IndustrialCore.MOD_ID, "ad"))),
                        set.toughness(),
                        set.knockback());
            });

    /** Phase 98: Virtual Aegis endgame powered armor (layer prefix "va" for short Windows path). */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VIRTUAL_AEGIS =
            ARMOR_MATERIALS.register("virtual_aegis", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 4,
                            ArmorItem.Type.CHESTPLATE, 9,
                            ArmorItem.Type.LEGGINGS, 7,
                            ArmorItem.Type.BOOTS, 4),
                    25,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(ModItems.QUANTUM_SHARD.get()),
                    List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(
                            IndustrialCore.MOD_ID, "va"))),
                    4.0F,
                    0.20F));

    private ModArmorMaterials() {}
}
