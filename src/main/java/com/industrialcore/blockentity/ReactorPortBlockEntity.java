package com.industrialcore.blockentity;

import com.industrialcore.machine.GenerationKind;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 103: BlockEntity for {@code reactor_port}. Delegates {@link IEnergyStorage} and
 * {@link IItemHandler} capabilities to the owning formed multiblock controller
 * ({@link GenerationBlockEntity} NUCLEAR or {@link SingularityReactorBlockEntity}).
 */
public class ReactorPortBlockEntity extends BlockEntity {
    private static final String TAG_CTRL_X = "CtrlX";
    private static final String TAG_CTRL_Y = "CtrlY";
    private static final String TAG_CTRL_Z = "CtrlZ";
    private static final String TAG_LINKED = "Linked";

    @Nullable
    private BlockPos controllerPos;

    public ReactorPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_PORT.get(), pos, state);
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void linkTo(@Nullable BlockPos ctrlPos) {
        BlockPos next = ctrlPos != null ? ctrlPos.immutable() : null;
        if ((this.controllerPos == null && next == null)
                || (this.controllerPos != null && this.controllerPos.equals(next))) {
            return;
        }
        this.controllerPos = next;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.invalidateCapabilities(worldPosition);
            FluidPipeBlockEntity.invalidateAllNetworks();
            HyperEnergyConduitBlockEntity.invalidateAllNetworks();
            HyperItemConduitBlockEntity.invalidateAllNetworks();
        }
    }

    public void unlink() {
        linkTo(null);
    }

    /** Returns the linked formed controller BlockEntity, or {@code null} if unlinked/unformed. */
    @Nullable
    public BlockEntity getLinkedController() {
        if (controllerPos == null || level == null || !level.isLoaded(controllerPos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(controllerPos);
        if (be instanceof GenerationBlockEntity gen
                && gen.kind() == GenerationKind.NUCLEAR
                && gen.isMultiblockFormed()) {
            return gen;
        }
        if (be instanceof SingularityReactorBlockEntity sr && sr.isMultiblockFormed()) {
            return sr;
        }
        return null;
    }

    @Nullable
    public IEnergyStorage getProxiedEnergyStorage() {
        BlockEntity ctrl = getLinkedController();
        if (ctrl instanceof GenerationBlockEntity gen) {
            return gen.getPortEnergyStorage();
        }
        if (ctrl instanceof SingularityReactorBlockEntity sr) {
            return sr.getEnergyStorage();
        }
        return null;
    }

    @Nullable
    public IItemHandler getProxiedItemHandler() {
        BlockEntity ctrl = getLinkedController();
        if (ctrl instanceof GenerationBlockEntity gen) {
            return gen.getInventory();
        }
        if (ctrl instanceof SingularityReactorBlockEntity sr) {
            return sr.getAutomationItemHandler();
        }
        return null;
    }

    /** Phase 105.6: Delegates water-coolant fluid intake to a linked formed Nuclear Generator. */
    @Nullable
    public IFluidHandler getProxiedFluidHandler() {
        BlockEntity ctrl = getLinkedController();
        if (ctrl instanceof GenerationBlockEntity gen && gen.kind() == GenerationKind.NUCLEAR) {
            return gen.getCoolantFluidHandler();
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putBoolean(TAG_LINKED, true);
            tag.putInt(TAG_CTRL_X, controllerPos.getX());
            tag.putInt(TAG_CTRL_Y, controllerPos.getY());
            tag.putInt(TAG_CTRL_Z, controllerPos.getZ());
        } else {
            tag.putBoolean(TAG_LINKED, false);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.getBoolean(TAG_LINKED)) {
            this.controllerPos = new BlockPos(
                    tag.getInt(TAG_CTRL_X),
                    tag.getInt(TAG_CTRL_Y),
                    tag.getInt(TAG_CTRL_Z));
        } else {
            this.controllerPos = null;
        }
    }
}
