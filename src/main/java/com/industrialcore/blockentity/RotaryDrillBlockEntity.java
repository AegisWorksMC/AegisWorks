package com.industrialcore.blockentity;

import com.industrialcore.block.RotaryDrillBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.RotaryDrillMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.trap.TrapLogic;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Phase 92: BlockEntity for the 6-direction Rotary Blade Drill ({@code rotary_drill}).
 *
 * <p>Deals FakePlayer melee damage across a 3x3x2 blade zone in front of {@link RotaryDrillBlock#FACING},
 * guaranteeing XP orbs and player-kill rare drops. Supports an optional weapon slot whose attack
 * bonus and enchantments (Looting, Smite, Fire Aspect, etc.) apply with zero durability loss while
 * never harming players or tamed/owned pets.</p>
 */
public class RotaryDrillBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    public static final String MACHINE_ID = "rotary_drill";
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();

    public static final int WEAPON_SLOT = 0;
    public static final int MOTOR_SLOT = 1;
    public static final int BATTERY_SLOT = 2;
    public static final int SLOT_COUNT = 3;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_DAMAGE_X10 = 5;
    public static final int DATA_KILLS = 6;
    public static final int DATA_COUNT = 7;

    private static final GameProfile DRILL_PROFILE = new GameProfile(
            UUID.fromString("7d4f8a21-6c55-4b88-9f12-0c3e9a542190"), "[IndustrialCore_Drill]");

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_KILLS = "Kills";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final DrillInventory inventory = new DrillInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            TrapLogic.DRILL_BASE_CAPACITY, TrapLogic.DRILL_MAX_RECEIVE);

    private int progress;
    private int killsTotal;
    private int activeGlowTicks;
    private MachineStatus status = MachineStatus.IDLE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> currentInterval();
                case DATA_DAMAGE_X10 -> Math.round(currentDisplayDamage() * 10.0F);
                case DATA_KILLS -> killsTotal;
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

    public RotaryDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROTARY_DRILL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RotaryDrillBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickServer(serverLevel, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int interval = currentInterval();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int attackCost = TrapLogic.drillAttackEnergy(getBatteryCount(), ownerLvl);

        if (energy.getEnergyStored() < attackCost) {
            status = MachineStatus.NO_ENERGY;
            progress = 0;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (activeGlowTicks > 0) {
            activeGlowTicks--;
        }

        progress++;
        if (progress < interval) {
            if (status == MachineStatus.NO_ENERGY) {
                status = activeGlowTicks > 0 ? MachineStatus.WORKING : MachineStatus.IDLE;
            }
            return;
        }
        progress = 0;

        Direction dir = state.hasProperty(RotaryDrillBlock.FACING)
                ? state.getValue(RotaryDrillBlock.FACING) : Direction.NORTH;
        TrapLogic.Box6 box6 = TrapLogic.drillVolume(
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                dir.getStepX(), dir.getStepY(), dir.getStepZ());
        AABB box = new AABB(box6.minX(), box6.minY(), box6.minZ(),
                box6.maxX(), box6.maxY(), box6.maxZ());

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, RotaryDrillBlockEntity::isValidTarget);
        if (targets.isEmpty()) {
            if (activeGlowTicks > 0) {
                status = MachineStatus.WORKING;
                updateLit(level, state, true);
            } else {
                status = MachineStatus.IDLE;
                updateLit(level, state, false);
            }
            return;
        }

        FakePlayer fakePlayer = FakePlayerFactory.get(level, DRILL_PROFILE);
        fakePlayer.setPos(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D);
        // Clone the weapon so Looting, Fire Aspect, and attribute bonuses apply with zero durability loss
        ItemStack weaponCopy = inventory.getStackInSlot(WEAPON_SLOT).copy();
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, weaponCopy);

        boolean hitAny = false;
        try {
            float baseDmg = currentDisplayDamage();
            for (LivingEntity target : targets) {
                if (!target.isAlive()) {
                    continue;
                }
                DamageSource src = level.damageSources().playerAttack(fakePlayer);
                float dmg = weaponCopy.isEmpty()
                        ? baseDmg
                        : EnchantmentHelper.modifyDamage(level, weaponCopy, target, src, baseDmg);
                boolean hurt = target.hurt(src, dmg);
                if (hurt) {
                    hitAny = true;
                    if (!weaponCopy.isEmpty()) {
                        EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, src, weaponCopy);
                    }
                    if (!target.isAlive()) {
                        killsTotal++;
                    }
                }
            }
        } finally {
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        if (hitAny) {
            energy.consume(attackCost);
            activeGlowTicks = 25;
            status = MachineStatus.WORKING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            setChanged();

            level.playSound(null, worldPosition, SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.BLOCKS, 0.22F, 1.35F);
        } else {
            status = activeGlowTicks > 0 ? MachineStatus.WORKING : MachineStatus.IDLE;
            updateLit(level, state, activeGlowTicks > 0);
        }
    }

    public static boolean isValidTarget(LivingEntity entity) {
        boolean isPlayer = entity instanceof Player;
        boolean isTamedOrOwned = (entity instanceof TamableAnimal ta && ta.isTame())
                || (entity instanceof AbstractHorse horse && horse.isTamed())
                || (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null);
        boolean isArmorStand = entity instanceof ArmorStand;
        return TrapLogic.canDrillAttack(isPlayer, isTamedOrOwned, isArmorStand,
                entity.isAlive() && !entity.isInvulnerable());
    }

    public static boolean isWeaponStack(ItemStack stack) {
        if (stack.isEmpty() || stack.is(ModItems.MOTOR.get()) || stack.is(ModItems.BATTERY.get())) {
            return false;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem) {
            return true;
        }
        boolean[] hasAtk = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.equals(Attributes.ATTACK_DAMAGE) && mod.amount() > 0.0D) {
                hasAtk[0] = true;
            }
        });
        return hasAtk[0];
    }

    public static float weaponDamageBonus(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        double[] sum = {0.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.equals(Attributes.ATTACK_DAMAGE)
                    && mod.operation() == AttributeModifier.Operation.ADD_VALUE
                    && mod.amount() > 0.0D) {
                sum[0] += mod.amount();
            }
        });
        return (float) sum[0];
    }

    public float currentDisplayDamage() {
        float base = TrapLogic.drillBaseDamage(getMotorCount())
                + weaponDamageBonus(inventory.getStackInSlot(WEAPON_SLOT));
        return (float) (base * UpgradeMath.turretDamageMultiplier(getSpecialCount()));
    }

    public int currentInterval() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return TrapLogic.drillAttackInterval(getMotorCount(), lvl);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(RotaryDrillBlock.LIT) && state.getValue(RotaryDrillBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(RotaryDrillBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(TrapLogic.DRILL_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int getMotorCount() {
        return upgradeBank.motors() + inventory.getStackInSlot(MOTOR_SLOT).getCount();
    }

    public int getBatteryCount() {
        return upgradeBank.batteries() + inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    public int getSpecialCount() {
        return upgradeBank.specials();
    }

    @Override
    public com.industrialcore.machine.VirtualUpgradeBank getUpgradeBank() {
        return upgradeBank;
    }

    @Override
    public com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory getSpecialCategory() {
        return com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory.HARVEST_MINE;
    }

    @Override
    public String getUpgradeMachineId() {
        return MACHINE_ID;
    }

    @Override
    public int getLegacyUpgradeCount() {
        return inventory.getStackInSlot(MOTOR_SLOT).getCount() + inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    @Override
    public int getTotalUpgradeLimit() {
        return (level instanceof ServerLevel sl)
                ? workTracker.totalUpgradeLimit(sl, MACHINE_ID)
                : com.industrialcore.level.MachineLevelMath.totalUpgradeLimitForLevel(1);
    }

    @Override
    public void onUpgradeBankChanged() {
        syncBatteryCapacity();
        setChanged();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return com.industrialcore.level.UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
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

    public MachineStatus getStatus() {
        return status;
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

    public void setCreativeOperator(boolean creative) {
        workTracker.setCreativeOperator(creative);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.rotary_drill");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new RotaryDrillMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_KILLS, killsTotal);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        upgradeBank.save(tag);
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        upgradeBank.load(tag);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        killsTotal = Math.max(0, tag.getInt(TAG_KILLS));
        workTracker.load(tag);
    }

    private static final class DrillInventory extends ItemStackHandler {
        private final RotaryDrillBlockEntity owner;

        DrillInventory(RotaryDrillBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == WEAPON_SLOT) {
                return isWeaponStack(stack);
            }
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
            if (slot == WEAPON_SLOT) {
                return 1;
            }
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
