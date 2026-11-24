package com.industrialcore.blockentity;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.block.WarpPadBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.life.LifeWave2Logic;
import com.industrialcore.life.LifeWave2Logic.WarpEntry;
import com.industrialcore.life.WarpNetworkData;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.WarpPadMenu;
import com.industrialcore.network.WarpActionPayload;
import com.industrialcore.network.WarpListSyncPayload;
import com.industrialcore.network.WarpListSyncPayload.TargetRow;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 95: BlockEntity for the Teleport Warp Pad ({@code warp_pad}).
 *
 * <p>Registers itself in {@link WarpNetworkData}, supports custom renaming and public/private
 * visibility, validates destination pad existence before teleporting (auto-cleaning broken
 * targets), and supports cross-dimension teleportation with motor/battery FE discounts.</p>
 */
public class WarpPadBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "warp_pad";

    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_COOLDOWN = 3;
    public static final int DATA_PUBLIC = 4;
    public static final int DATA_COUNT = 5;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_NAME = "PadName";
    private static final String TAG_PUBLIC = "Public";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final WarpInventory inventory = new WarpInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            LifeWave2Logic.WARP_BASE_CAPACITY, LifeWave2Logic.WARP_MAX_RECEIVE);

    private String padName = "";
    private boolean isPublic = true;
    private int cooldownTicks;
    private int warpState = LifeWave2Logic.WARP_STATE_IDLE;
    private boolean registeredThisSession;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATE -> warpState;
                case DATA_COOLDOWN -> cooldownTicks;
                case DATA_PUBLIC -> isPublic ? 1 : 0;
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

    public WarpPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WARP_PAD.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WarpPadBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        if (!registeredThisSession) {
            ensureRegistered(level);
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (cooldownTicks > 0) {
            warpState = LifeWave2Logic.WARP_STATE_COOLDOWN;
            updateLit(level, state, true);
        } else if (energy.getEnergyStored() < 100) {
            warpState = LifeWave2Logic.WARP_STATE_NO_ENERGY;
            updateLit(level, state, false);
        } else {
            warpState = LifeWave2Logic.WARP_STATE_IDLE;
            updateLit(level, state, energy.getEnergyStored() >= LifeWave2Logic.WARP_BASE_COST);
        }
    }

    public String getSelfPadId(ServerLevel level) {
        return WarpNetworkData.padId(level.dimension(), worldPosition);
    }

    public void ensureRegistered(ServerLevel level) {
        WarpEntry entry = WarpNetworkData.get(level).registerPad(
                level, worldPosition, workTracker.ownerUuid(), padName, isPublic);
        padName = entry.name();
        registeredThisSession = true;
    }

    public void syncListToPlayer(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        ensureRegistered(sl);
        WarpNetworkData data = WarpNetworkData.get(sl);
        String selfId = getSelfPadId(sl);
        String selfDim = sl.dimension().location().toString();
        int motors = getMotorCount();
        int batteries = getBatteryCount();
        int ownerLvl = workTracker.ownerLevel(sl, MACHINE_ID);

        List<WarpEntry> visible = data.visibleTargetsFor(selfId, player.getUUID());
        List<TargetRow> rows = new ArrayList<>(visible.size());
        for (WarpEntry e : visible) {
            boolean cross = !selfDim.equals(e.dimensionId());
            double dist = cross ? 0.0D
                    : Math.sqrt(worldPosition.distSqr(new BlockPos(e.x(), e.y(), e.z())));
            int cost = LifeWave2Logic.warpCostFe(dist, cross, motors, batteries, ownerLvl);
            rows.add(new TargetRow(e.padId(), e.name(), e.dimensionId(),
                    (int) Math.round(dist), cost, cross, e.isPublic()));
        }
        PacketDistributor.sendToPlayer(player,
                new WarpListSyncPayload(worldPosition, padName, isPublic, rows));
    }

    public boolean handleAction(ServerPlayer player, int action, String value) {
        if (!(level instanceof ServerLevel sl)) {
            return false;
        }
        ensureRegistered(sl);
        WarpNetworkData data = WarpNetworkData.get(sl);
        String selfId = getSelfPadId(sl);

        switch (action) {
            case WarpActionPayload.ACTION_RENAME -> {
                String clean = LifeWave2Logic.sanitizePadName(value,
                        worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
                padName = clean;
                data.renamePad(selfId, clean);
                setChanged();
                syncListToPlayer(player);
                return true;
            }
            case WarpActionPayload.ACTION_TOGGLE_PUBLIC -> {
                isPublic = !isPublic;
                data.setPublic(selfId, isPublic);
                setChanged();
                syncListToPlayer(player);
                return true;
            }
            case WarpActionPayload.ACTION_REQUEST_SYNC -> {
                syncListToPlayer(player);
                return true;
            }
            case WarpActionPayload.ACTION_TELEPORT -> {
                return performTeleport(sl, player, value);
            }
            default -> {
                return false;
            }
        }
    }

    public boolean performTeleport(ServerLevel sl, ServerPlayer player, String targetPadId) {
        if (cooldownTicks > 0) {
            return false;
        }
        WarpNetworkData data = WarpNetworkData.get(sl);
        WarpEntry target = data.get(targetPadId);
        if (target == null || !LifeWave2Logic.canAccessPad(target, player.getUUID().toString())) {
            player.displayClientMessage(Component.translatable("gui.industrialcore.warp_pad.err_missing")
                    .withStyle(ChatFormatting.RED), true);
            syncListToPlayer(player);
            return false;
        }

        MinecraftServer server = sl.getServer();
        ResourceLocation dimLoc = ResourceLocation.tryParse(target.dimensionId());
        ServerLevel targetLevel = dimLoc == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
        BlockPos targetPos = new BlockPos(target.x(), target.y(), target.z());

        // Mandatory safety guard: verify destination WarpPadBlock physically exists
        if (targetLevel == null || !(targetLevel.getBlockState(targetPos).getBlock() instanceof WarpPadBlock)) {
            data.unregisterPad(target.padId());
            player.displayClientMessage(Component.translatable("gui.industrialcore.warp_pad.err_missing")
                    .withStyle(ChatFormatting.RED), true);
            syncListToPlayer(player);
            return false;
        }

        boolean cross = !sl.dimension().equals(targetLevel.dimension());
        double dist = cross ? 0.0D : Math.sqrt(worldPosition.distSqr(targetPos));
        int motors = getMotorCount();
        int batteries = getBatteryCount();
        int ownerLvl = workTracker.ownerLevel(sl, MACHINE_ID);
        int cost = LifeWave2Logic.warpCostFe(dist, cross, motors, batteries, ownerLvl);
        if (energy.getEnergyStored() < cost) {
            warpState = LifeWave2Logic.WARP_STATE_NO_ENERGY;
            player.displayClientMessage(Component.translatable("gui.industrialcore.machine.status.no_energy")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        energy.consume(cost);
        cooldownTicks = LifeWave2Logic.warpCooldownTicks(motors, ownerLvl);
        warpState = LifeWave2Logic.WARP_STATE_COOLDOWN;
        workTracker.recordActiveTick(sl, MACHINE_ID);

        // Departure chime (ambient portal particles handled client-side via animateTick)
        sl.playSound(null, worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.55F, 1.15F);

        player.closeContainer();
        player.teleportTo(targetLevel,
                targetPos.getX() + 0.5D, targetPos.getY() + 0.38D, targetPos.getZ() + 0.5D,
                player.getYRot(), player.getXRot());

        // Arrival pleasant amethyst chime
        targetLevel.playSound(null, targetPos, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.85F, 1.12F);
        setChanged();
        return true;
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractLifeBlock.LIT) && state.getValue(AbstractLifeBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractLifeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(LifeWave2Logic.WARP_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int getMotorCount() {
        return inventory.getStackInSlot(MOTOR_SLOT).getCount();
    }

    public int getBatteryCount() {
        return inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public String getPadName() {
        return padName;
    }

    public boolean isPublicPad() {
        return isPublic;
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
        return Component.translatable("block.industrialcore.warp_pad");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new WarpPadMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putString(TAG_NAME, padName);
        tag.putBoolean(TAG_PUBLIC, isPublic);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        padName = tag.getString(TAG_NAME);
        isPublic = !tag.contains(TAG_PUBLIC) || tag.getBoolean(TAG_PUBLIC);
        workTracker.load(tag);
    }

    private static final class WarpInventory extends ItemStackHandler {
        private final WarpPadBlockEntity owner;

        WarpInventory(WarpPadBlockEntity owner) {
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
            if (slot == MOTOR_SLOT || slot == BATTERY_SLOT) {
                return owner.getUpgradeLimit();
            }
            return super.getSlotLimit(slot);
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
