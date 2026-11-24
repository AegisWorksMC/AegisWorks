package com.industrialcore.blockentity;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.item.ElectricWeaponItem;
import com.industrialcore.item.PulseLaserItem;
import com.industrialcore.item.SuitModuleItem;
import com.industrialcore.item.VirtualSuitArmorItem;
import com.industrialcore.item.WeaponAttachmentItem;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.menu.SuitModifierMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModSounds;
import com.industrialcore.suit.SuitModule;
import com.industrialcore.suit.VirtualSuitLogic;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.WeaponAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 98: BlockEntity for the Suit Module Modifier ({@code suit_modifier}).
 *
 * <p>Installs, toggles ON/OFF, and uninstalls {@link SuitModule}s on any {@link VirtualSuitArmorItem},
 * while also rapidly charging any Virtual Aegis armor piece placed in slot 0. Follows Phase 87+
 * architecture: trailing motor &amp; battery slots with {@link UpgradeCapBridge}, no 6-face config,
 * drops all contents on break.</p>
 */
public class SuitModifierBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "suit_modifier";

    public static final int SUIT_SLOT = 0;
    public static final int MODULE_SLOT = 1;
    public static final int MOTOR_SLOT = 2;
    public static final int BATTERY_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int DATA_ENERGY_LO = 0;
    public static final int DATA_ENERGY_HI = 1;
    public static final int DATA_CAP_LO = 2;
    public static final int DATA_CAP_HI = 3;
    public static final int DATA_STATE = 4;
    public static final int DATA_INSTALL_COST = 5;
    public static final int DATA_CHARGE_RATE = 6;
    public static final int DATA_COUNT = 7;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_INV = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final ModifierInventory inventory = new ModifierInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            VirtualSuitLogic.MODIFIER_BASE_CAPACITY, VirtualSuitLogic.MODIFIER_MAX_RECEIVE);

    private int displayState = VirtualSuitLogic.STATE_IDLE;
    private int activeGlowTicks = 0;
    private int particleTick = 0;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            int e = energy.getEnergyStored();
            int cap = energy.getMaxEnergyStored();
            int ownerLvl = level instanceof ServerLevel sl ? workTracker.ownerLevel(sl, MACHINE_ID) : 1;
            return switch (index) {
                case DATA_ENERGY_LO -> e & 0xFFFF;
                case DATA_ENERGY_HI -> (e >>> 16) & 0xFFFF;
                case DATA_CAP_LO -> cap & 0xFFFF;
                case DATA_CAP_HI -> (cap >>> 16) & 0xFFFF;
                case DATA_STATE -> displayState;
                case DATA_INSTALL_COST -> Math.min(Short.MAX_VALUE,
                        VirtualSuitLogic.modifierInstallCost(getBatteryCount(), ownerLvl));
                case DATA_CHARGE_RATE -> Math.min(Short.MAX_VALUE,
                        VirtualSuitLogic.modifierChargeRate(getMotorCount(), ownerLvl) / 100);
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

    public SuitModifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUIT_MODIFIER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SuitModifierBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int motors = getMotorCount();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);

        if (activeGlowTicks > 0) {
            activeGlowTicks--;
        }

        ItemStack suit = inventory.getStackInSlot(SUIT_SLOT);
        if (suit.getItem() instanceof VirtualSuitArmorItem || suit.getItem() instanceof ElectricWeaponItem) {
            boolean isWeapon = suit.getItem() instanceof ElectricWeaponItem;
            int room = isWeapon
                    ? VirtualWeaponLogic.WEAPON_CAPACITY - ElectricWeaponItem.getEnergy(suit)
                    : VirtualSuitLogic.PIECE_CAPACITY - VirtualSuitArmorItem.getEnergy(suit);
            if (room > 0) {
                if (energy.getEnergyStored() <= 0) {
                    displayState = VirtualSuitLogic.STATE_NO_ENERGY;
                    updateLit(level, state, false);
                    return;
                }
                int rate = VirtualSuitLogic.modifierChargeRate(motors, ownerLvl);
                int offer = Math.min(rate, Math.min(room, energy.getEnergyStored()));
                int accepted = isWeapon
                        ? ElectricWeaponItem.receiveEnergy(suit, offer, false)
                        : VirtualSuitArmorItem.receiveEnergy(suit, offer, false);
                if (accepted > 0) {
                    energy.consume(accepted);
                    inventory.setStackInSlot(SUIT_SLOT, suit);
                    displayState = VirtualSuitLogic.STATE_CHARGING;
                    activeGlowTicks = 12;
                    updateLit(level, state, true);
                    setChanged();
                    return;
                }
            }
        }

        displayState = energy.getEnergyStored() <= 0
                ? VirtualSuitLogic.STATE_NO_ENERGY
                : VirtualSuitLogic.STATE_IDLE;
        updateLit(level, state, activeGlowTicks > 0 || !suit.isEmpty());
    }

    // -------------------------------------------------------------------------
    // C2S Module Actions
    // -------------------------------------------------------------------------

    public boolean tryInstallModule(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) {
            return false;
        }
        ItemStack suit = inventory.getStackInSlot(SUIT_SLOT);
        ItemStack modStack = inventory.getStackInSlot(MODULE_SLOT);

        if (suit.getItem() instanceof VirtualSuitArmorItem suitItem
                && modStack.getItem() instanceof SuitModuleItem modItem) {
            SuitModule module = modItem.getModule();
            Map<String, Boolean> mods = VirtualSuitArmorItem.getModules(suit);
            if (!VirtualSuitLogic.canInstall(suitItem.getSlotKind(), module, mods)) {
                return false;
            }
            int ownerLvl = workTracker.ownerLevel(sl, MACHINE_ID);
            int cost = VirtualSuitLogic.modifierInstallCost(getBatteryCount(), ownerLvl);
            if (!player.isCreative()) {
                if (energy.getEnergyStored() < cost) {
                    return false;
                }
                energy.consume(cost);
            }
            VirtualSuitLogic.install(suitItem.getSlotKind(), module, mods);
            VirtualSuitArmorItem.setModules(suit, mods);
            modStack.shrink(1);
            inventory.setStackInSlot(SUIT_SLOT, suit);
            inventory.setStackInSlot(MODULE_SLOT, modStack);
            activeGlowTicks = 30;
            sl.playSound(null, worldPosition, ModSounds.MACHINE_COMPLETE.get(), SoundSource.BLOCKS, 0.7F, 1.15F);
            setChanged();
            return true;
        }

        if (suit.getItem() instanceof ElectricWeaponItem weaponItem
                && modStack.getItem() instanceof WeaponAttachmentItem attItem) {
            WeaponAttachment att = attItem.getAttachment();
            Map<String, Boolean> atts = ElectricWeaponItem.getAttachments(suit);
            if (!VirtualWeaponLogic.canInstall(weaponItem.getWeaponKind(), att, atts)) {
                return false;
            }
            int ownerLvl = workTracker.ownerLevel(sl, MACHINE_ID);
            int cost = VirtualSuitLogic.modifierInstallCost(getBatteryCount(), ownerLvl);
            if (!player.isCreative()) {
                if (energy.getEnergyStored() < cost) {
                    return false;
                }
                energy.consume(cost);
            }
            VirtualWeaponLogic.install(weaponItem.getWeaponKind(), att, atts);
            ElectricWeaponItem.setAttachments(suit, atts);
            modStack.shrink(1);
            inventory.setStackInSlot(SUIT_SLOT, suit);
            inventory.setStackInSlot(MODULE_SLOT, modStack);
            activeGlowTicks = 30;
            sl.playSound(null, worldPosition, ModSounds.MACHINE_COMPLETE.get(), SoundSource.BLOCKS, 0.7F, 1.15F);
            setChanged();
            return true;
        }

        return false;
    }

    public boolean tryToggleModule(ServerPlayer player, int rowIndex) {
        ItemStack suit = inventory.getStackInSlot(SUIT_SLOT);
        if (suit.getItem() instanceof VirtualSuitArmorItem suitItem) {
            List<SuitModule> slotMods = SuitModule.forSlot(suitItem.getSlotKind());
            if (rowIndex < 0 || rowIndex >= slotMods.size()) {
                return false;
            }
            SuitModule target = slotMods.get(rowIndex);
            Map<String, Boolean> mods = VirtualSuitArmorItem.getModules(suit);
            if (!VirtualSuitLogic.toggle(target, mods)) {
                return false;
            }
            VirtualSuitArmorItem.setModules(suit, mods);
            inventory.setStackInSlot(SUIT_SLOT, suit);
            if (level instanceof ServerLevel sl) {
                sl.playSound(null, worldPosition, ModSounds.UI_CLICK.get(), SoundSource.BLOCKS, 0.55F, 1.1F);
            }
            setChanged();
            return true;
        }
        if (suit.getItem() instanceof ElectricWeaponItem weaponItem) {
            List<WeaponAttachment> attsList = WeaponAttachment.forWeapon(weaponItem.getWeaponKind());
            if (rowIndex < 0 || rowIndex >= attsList.size()) {
                return false;
            }
            WeaponAttachment target = attsList.get(rowIndex);
            Map<String, Boolean> atts = ElectricWeaponItem.getAttachments(suit);
            if (!VirtualWeaponLogic.toggle(target, atts)) {
                return false;
            }
            ElectricWeaponItem.setAttachments(suit, atts);
            inventory.setStackInSlot(SUIT_SLOT, suit);
            if (level instanceof ServerLevel sl) {
                sl.playSound(null, worldPosition, ModSounds.UI_CLICK.get(), SoundSource.BLOCKS, 0.55F, 1.1F);
            }
            setChanged();
            return true;
        }
        return false;
    }

    public boolean tryRemoveModule(ServerPlayer player, int rowIndex) {
        ItemStack suit = inventory.getStackInSlot(SUIT_SLOT);
        ItemStack returned = ItemStack.EMPTY;

        if (suit.getItem() instanceof VirtualSuitArmorItem suitItem) {
            List<SuitModule> slotMods = SuitModule.forSlot(suitItem.getSlotKind());
            if (rowIndex < 0 || rowIndex >= slotMods.size()) {
                return false;
            }
            SuitModule target = slotMods.get(rowIndex);
            Map<String, Boolean> mods = VirtualSuitArmorItem.getModules(suit);
            if (!VirtualSuitLogic.uninstall(target, mods)) {
                return false;
            }
            VirtualSuitArmorItem.setModules(suit, mods);
            inventory.setStackInSlot(SUIT_SLOT, suit);
            returned = new ItemStack(itemForModule(target));
        } else if (suit.getItem() instanceof ElectricWeaponItem weaponItem) {
            List<WeaponAttachment> attsList = WeaponAttachment.forWeapon(weaponItem.getWeaponKind());
            if (rowIndex < 0 || rowIndex >= attsList.size()) {
                return false;
            }
            WeaponAttachment target = attsList.get(rowIndex);
            Map<String, Boolean> atts = ElectricWeaponItem.getAttachments(suit);
            if (!VirtualWeaponLogic.uninstall(target, atts)) {
                return false;
            }
            ElectricWeaponItem.setAttachments(suit, atts);
            inventory.setStackInSlot(SUIT_SLOT, suit);
            returned = new ItemStack(itemForAttachment(target));
        } else {
            return false;
        }

        // Return removed module/attachment item to slot 1, then player inventory, else drop safely
        ItemStack existing = inventory.getStackInSlot(MODULE_SLOT);
        if (existing.isEmpty()) {
            inventory.setStackInSlot(MODULE_SLOT, returned);
        } else if (ItemStack.isSameItemSameComponents(existing, returned)
                && existing.getCount() < existing.getMaxStackSize()) {
            existing.grow(1);
            inventory.setStackInSlot(MODULE_SLOT, existing);
        } else if (!player.getInventory().add(returned)) {
            Containers.dropItemStack(player.level(),
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, returned);
        }

        if (level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, ModSounds.UI_CLICK.get(), SoundSource.BLOCKS, 0.65F, 0.85F);
        }
        setChanged();
        return true;
    }

    /** Cycles {@link com.industrialcore.weapon.LaserAimRange} on a Pulse Laser placed in slot 0. */
    public boolean tryCycleLaserRange(ServerPlayer player) {
        ItemStack suit = inventory.getStackInSlot(SUIT_SLOT);
        if (!(suit.getItem() instanceof PulseLaserItem)) {
            return false;
        }
        PulseLaserItem.toggleAimRange(player, suit);
        inventory.setStackInSlot(SUIT_SLOT, suit);
        setChanged();
        return true;
    }

    public static Item itemForModule(SuitModule module) {
        return switch (module) {
            case VISION -> ModItems.MOD_VISION.get();
            case AUTOFEED -> ModItems.MOD_AUTOFEED.get();
            case FLIGHT -> ModItems.MOD_FLIGHT.get();
            case SHIELD -> ModItems.MOD_SHIELD.get();
            case SPEED -> ModItems.MOD_SPEED.get();
            case STEP -> ModItems.MOD_STEP.get();
            case NOFALL -> ModItems.MOD_NOFALL.get();
            case JUMP -> ModItems.MOD_JUMP.get();
        };
    }

    public static Item itemForAttachment(WeaponAttachment att) {
        return switch (att) {
            case AUTO_AIM -> ModItems.ATT_AUTO_AIM.get();
            case CRYO_CORE -> ModItems.ATT_CRYO_CORE.get();
            case PRISM_LENS -> ModItems.ATT_PRISM_LENS.get();
            case FAST_CAPACITOR -> ModItems.ATT_FAST_CAPACITOR.get();
            case WALL_SCANNER -> ModItems.ATT_WALL_SCANNER.get();
            case RECOIL_GYRO -> ModItems.ATT_RECOIL_GYRO.get();
            case PLASMA_WAVE -> ModItems.ATT_PLASMA_WAVE.get();
            case BLINK_DRIVE -> ModItems.ATT_BLINK_DRIVE.get();
            case QUANTUM_LOOT -> ModItems.ATT_QUANTUM_LOOT.get();
            case MULTI_LOCK -> ModItems.ATT_MULTI_LOCK.get();
            case GRAVITY_ARROW -> ModItems.ATT_GRAVITY_ARROW.get();
            case AUTO_DRAW -> ModItems.ATT_AUTO_DRAW.get();
        };
    }

    // -------------------------------------------------------------------------
    // Helpers, upgrades, capabilities, NBT
    // -------------------------------------------------------------------------

    private void syncBatteryCapacity() {
        int cap = VirtualSuitLogic.modifierCapacity(getBatteryCount());
        if (energy.getMaxEnergyStored() != cap) {
            energy.setCapacity(cap);
            setChanged();
        }
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractLifeBlock.LIT) && state.getValue(AbstractLifeBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractLifeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    public int getMotorCount() {
        ItemStack s = inventory.getStackInSlot(MOTOR_SLOT);
        return s.is(ModItems.MOTOR.get()) ? s.getCount() : 0;
    }

    public int getBatteryCount() {
        ItemStack s = inventory.getStackInSlot(BATTERY_SLOT);
        return s.is(ModItems.BATTERY.get()) ? s.getCount() : 0;
    }

    public int getUpgradeSlotCap(@Nullable Player player) {
        return UpgradeCapBridge.effectiveLimit(player, this, MACHINE_ID);
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    @Override
    public String getMachineProgressId() {
        return MACHINE_ID;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    @Override
    @Nullable
    public UUID getOwnerUuid() {
        return workTracker.ownerUuid();
    }

    @Override
    public void setOwnerUuid(@Nullable UUID uuid) {
        workTracker.setOwnerUuid(uuid);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.suit_modifier");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SuitModifierMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.put(TAG_INV, inventory.serializeNBT(registries));
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        workTracker.load(tag);
        if (tag.contains(TAG_INV)) {
            inventory.deserializeNBT(registries, tag.getCompound(TAG_INV));
        }
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
    }

    private static final class ModifierInventory extends ItemStackHandler {
        private final SuitModifierBlockEntity owner;

        ModifierInventory(SuitModifierBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        protected void onContentsChanged(int slot) {
            owner.setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SUIT_SLOT -> stack.getItem() instanceof VirtualSuitArmorItem
                        || stack.getItem() instanceof ElectricWeaponItem;
                case MODULE_SLOT -> stack.getItem() instanceof SuitModuleItem
                        || stack.getItem() instanceof WeaponAttachmentItem;
                case MOTOR_SLOT -> stack.is(ModItems.MOTOR.get());
                case BATTERY_SLOT -> stack.is(ModItems.BATTERY.get());
                default -> false;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == SUIT_SLOT) {
                return 1;
            }
            if (slot == MODULE_SLOT) {
                return 16;
            }
            if (slot == MOTOR_SLOT || slot == BATTERY_SLOT) {
                return owner.getUpgradeLimit();
            }
            return super.getSlotLimit(slot);
        }
    }
}
