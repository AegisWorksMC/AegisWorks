package com.industrialcore.compat.jade;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.blockentity.SingularityReactorBlockEntity;
import com.industrialcore.endgame.SingularityReactorLogic;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineProgressSavedData;
import com.industrialcore.level.MachineSkillTree;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.IUpgradeableMachine;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.MachineStopReason;
import com.industrialcore.reactor.NuclearCoolantLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.UUID;

/**
 * Phase 114: Server-side Jade telemetry provider for all AegisWorks machines, generators,
 * reactors, energy buffers, and conduits.
 */
public enum AegisWorksJadeDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final String KEY_PRESENT = "AwJade";
    static final String KEY_ENERGY = "AwEnergy";
    static final String KEY_CAPACITY = "AwCap";
    static final String KEY_RATE = "AwRate";
    static final String KEY_HAS_ENERGY = "AwHasE";
    static final String KEY_LEVEL = "AwLv";
    static final String KEY_LIMIT_BROKEN = "AwLb";
    static final String KEY_MOTORS = "AwM";
    static final String KEY_BATTERIES = "AwB";
    static final String KEY_SPECIALS = "AwS";
    static final String KEY_HAS_UPGRADES = "AwHasU";
    static final String KEY_WORKING = "AwWork";
    static final String KEY_PROGRESS_PCT = "AwPct";
    static final String KEY_STOP_REASON = "AwStop";
    static final String KEY_OVERDRIVE = "AwOd";
    static final String KEY_COOLDOWN_SEC = "AwCd";
    static final String KEY_COOLANT_MB = "AwCool";
    static final String KEY_COOLANT_MAX = "AwCoolMax";

    @Override
    public ResourceLocation getUid() {
        return AegisWorksJadePlugin.UID;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (be == null || !(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(accessor.getBlock());
        if (blockKey == null || !IndustrialCore.MOD_ID.equals(blockKey.getNamespace())) {
            return;
        }

        tag.putBoolean(KEY_PRESENT, true);
        BlockPos pos = accessor.getPosition();
        boolean redstoneHalted = level.hasNeighborSignal(pos);

        // 1. Energy & Net FE/t Rate + explicit working/stop reason
        if (be instanceof SingularityReactorBlockEntity sing) {
            tag.putBoolean(KEY_HAS_ENERGY, true);
            tag.putLong(KEY_ENERGY, sing.getStoredEnergyLong());
            tag.putLong(KEY_CAPACITY, sing.getEnergyCapacityLong());
            tag.putLong(KEY_RATE, sing.getDisplayState() == SingularityReactorLogic.STATE_GENERATING
                    ? sing.currentFePerTick() : 0L);
            tag.putBoolean(KEY_OVERDRIVE, sing.isOverdriveMode());
            int cd = sing.getOverdriveCooldownTicks();
            if (cd > 0) {
                tag.putInt(KEY_COOLDOWN_SEC, Math.max(1, (cd + 19) / 20));
            }
            MachineStopReason stop = MachineStopReason.fromSingularityState(sing.getDisplayState(), cd > 0);
            tag.putBoolean(KEY_WORKING, sing.getDisplayState() == SingularityReactorLogic.STATE_GENERATING);
            tag.putString(KEY_STOP_REASON, stop.name());
            tag.putBoolean(KEY_HAS_UPGRADES, true);
            tag.putInt(KEY_MOTORS, sing.getMotorCount());
            tag.putInt(KEY_BATTERIES, sing.getBatteryCount());
            tag.putInt(KEY_SPECIALS, 0);
        } else if (be instanceof GenerationBlockEntity gen) {
            tag.putBoolean(KEY_HAS_ENERGY, true);
            tag.putLong(KEY_ENERGY, gen.getEnergyStorage().getEnergyStored());
            tag.putLong(KEY_CAPACITY, gen.getEnergyStorage().getMaxEnergyStored());
            tag.putLong(KEY_RATE, gen.getRate());
            boolean isNuclear = gen.kind() == GenerationKind.NUCLEAR;
            if (isNuclear) {
                tag.putInt(KEY_COOLANT_MB, gen.getCoolantMb());
                tag.putInt(KEY_COOLANT_MAX, NuclearCoolantLogic.TANK_CAPACITY_MB);
                tag.putBoolean(KEY_OVERDRIVE, gen.isOverdriveMode());
                int cd = gen.getOverdriveCooldownTicks();
                if (cd > 0) {
                    tag.putInt(KEY_COOLDOWN_SEC, Math.max(1, (cd + 19) / 20));
                }
            }
            MachineStopReason stop = MachineStopReason.fromGeneratorStatus(
                    gen.getStatus().ordinal(), isNuclear, gen.getCoolantMb(), gen.getOverdriveCooldownTicks() > 0);
            tag.putBoolean(KEY_WORKING, gen.getStatus() == GenerationBlockEntity.Status.RUNNING && gen.getRate() > 0);
            tag.putString(KEY_STOP_REASON, stop.name());
        } else if (be instanceof RecoveryBlockEntity rec) {
            tag.putBoolean(KEY_HAS_ENERGY, true);
            tag.putLong(KEY_ENERGY, rec.getEnergyStorage().getEnergyStored());
            tag.putLong(KEY_CAPACITY, rec.getEnergyStorage().getMaxEnergyStored());
            tag.putLong(KEY_RATE, rec.getRate());
            boolean running = rec.getRate() > 0;
            tag.putBoolean(KEY_WORKING, running);
            tag.putString(KEY_STOP_REASON, (running ? MachineStopReason.NONE : MachineStopReason.WAITING_INPUT).name());
        } else if (be instanceof ProcessingBlockEntity proc) {
            tag.putBoolean(KEY_HAS_ENERGY, true);
            tag.putLong(KEY_ENERGY, proc.getEnergyStorage().getEnergyStored());
            tag.putLong(KEY_CAPACITY, proc.getEnergyStorage().getMaxEnergyStored());
            MachineStatus st = proc.getStatus();
            boolean working = st == MachineStatus.WORKING;
            tag.putBoolean(KEY_WORKING, working);
            if (working) {
                tag.putLong(KEY_RATE, -proc.getKind().defaultEnergyPerTick());
                int op = Math.max(1, proc.getOperationTime());
                tag.putInt(KEY_PROGRESS_PCT, Math.min(100, proc.getProgress() * 100 / op));
            }
            MachineStopReason stop = MachineStopReason.fromMachineStatus(st, false);
            tag.putString(KEY_STOP_REASON, stop.name());
        } else if (be instanceof EnergyBufferBlockEntity buf) {
            tag.putBoolean(KEY_HAS_ENERGY, true);
            tag.putLong(KEY_ENERGY, buf.getEnergyStorage().getEnergyStored());
            tag.putLong(KEY_CAPACITY, buf.getEnergyStorage().getMaxEnergyStored());
            tag.putBoolean(KEY_WORKING, buf.getEnergyStorage().getEnergyStored() > 0);
            tag.putString(KEY_STOP_REASON, MachineStopReason.NONE.name());
        } else {
            // Generic AegisWorks machines / conduits exposing NeoForge Energy capability
            IEnergyStorage es = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            if (es != null && es.getMaxEnergyStored() > 0) {
                tag.putBoolean(KEY_HAS_ENERGY, true);
                tag.putLong(KEY_ENERGY, es.getEnergyStored());
                tag.putLong(KEY_CAPACITY, es.getMaxEnergyStored());
            }
            boolean lit = accessor.getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && accessor.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
            tag.putBoolean(KEY_WORKING, lit && !redstoneHalted);
            MachineStopReason stop = redstoneHalted
                    ? MachineStopReason.REDSTONE_DISABLED
                    : (es != null && es.getMaxEnergyStored() > 0 && es.getEnergyStored() <= 0
                            ? MachineStopReason.NO_ENERGY
                            : MachineStopReason.NONE);
            tag.putString(KEY_STOP_REASON, stop.name());
        }

        // 2. Virtual Upgrade Bank (M / B / S) for any IUpgradeableMachine
        if (be instanceof IUpgradeableMachine upg) {
            var bank = upg.getUpgradeBank();
            tag.putBoolean(KEY_HAS_UPGRADES, true);
            tag.putInt(KEY_MOTORS, bank.motors());
            tag.putInt(KEY_BATTERIES, bank.batteries());
            tag.putInt(KEY_SPECIALS, bank.specials());
        }

        // 3. Machine Level & Limit-Break state (when tracked in Skill Tree)
        String machineId = blockKey.getPath();
        if (be instanceof IOwnedMachine owned && owned.getMachineProgressId() != null) {
            machineId = owned.getMachineProgressId();
        }
        if (MachineSkillTree.isTracked(machineId)) {
            UUID lookupUuid = (be instanceof IOwnedMachine owned && owned.getOwnerUuid() != null)
                    ? owned.getOwnerUuid()
                    : (accessor.getPlayer() != null ? accessor.getPlayer().getUUID() : null);
            if (lookupUuid != null) {
                MachineProgressSavedData data = MachineProgressSavedData.get(level);
                int lvl = data.getLevel(lookupUuid, machineId);
                boolean lb = MachineProgressSavedData.isOwnerBranchLimitBroken(level, lookupUuid, machineId);
                tag.putInt(KEY_LEVEL, lvl);
                tag.putBoolean(KEY_LIMIT_BROKEN, lb);
            }
        }
    }
}
