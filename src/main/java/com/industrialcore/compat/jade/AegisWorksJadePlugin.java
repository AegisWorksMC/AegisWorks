package com.industrialcore.compat.jade;

import com.industrialcore.IndustrialCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Phase 114: Optional Jade (1.21.1 NeoForge) plugin for AegisWorks.
 *
 * <p>Strictly isolated inside {@code com.industrialcore.compat.jade} and loaded only via Jade's
 * {@link WailaPlugin} discovery when Jade is installed. Never referenced by any core mod class so
 * environments without Jade never load any {@code snownee.jade.*} bytecode.</p>
 */
@WailaPlugin
public final class AegisWorksJadePlugin implements IWailaPlugin {
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "machine_telemetry");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(AegisWorksJadeDataProvider.INSTANCE, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(AegisWorksJadeComponentProvider.INSTANCE, Block.class);
    }
}
