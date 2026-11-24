package com.industrialcore.blockentity;

import com.industrialcore.block.XpCollectorBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.XpCollectorMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.trap.TrapWave2Layout;
import com.industrialcore.trap.TrapWave2Logic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Phase 93: BlockEntity for the Experience Collector ({@code xp_collector}).
 *
 * <p>Vacuum-collects {@link ExperienceOrb} entities within 10..20 blocks into a 10M XP internal
 * tank, supports one-click player withdrawal ({@code +1 Lv}, {@code +10 Lv}, {@code All}), and
 * auto-bottles {@link Items#GLASS_BOTTLE} into {@link Items#EXPERIENCE_BOTTLE} (7 XP + FE per
 * bottle) into 3 upgrade-protected output slots.</p>
 */
public class XpCollectorBlockEntity extends BlockEntity
        implements MenuProvider, IOwnedMachine, com.industrialcore.machine.IUpgradeableMachine {
    public static final String MACHINE_ID = "xp_collector";
    private final com.industrialcore.machine.VirtualUpgradeBank upgradeBank = new com.industrialcore.machine.VirtualUpgradeBank();

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_COUNT = TrapWave2Layout.XP_OUTPUT_COUNT; // 3
    public static final int MOTOR_SLOT = 4;
    public static final int BATTERY_SLOT = 5;
    public static final int SLOT_COUNT = 6;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_XP_LO = 5;
    public static final int DATA_XP_HI = 6;
    public static final int DATA_RADIUS = 7;
    public static final int DATA_BOTTLED = 8;
    public static final int DATA_COUNT = 9;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_STORED_XP = "StoredXp";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_BOTTLED = "Bottled";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final XpInventory inventory = new XpInventory(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(
            TrapWave2Logic.XP_BASE_CAPACITY, TrapWave2Logic.XP_MAX_RECEIVE);

    private long storedXp;
    private int scanTicks;
    private int bottleProgress;
    private int bottledTotal;
    private int activeGlowTicks;
    private MachineStatus status = MachineStatus.IDLE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> bottleProgress;
                case DATA_TOTAL -> currentBottleTime();
                case DATA_XP_LO -> (int) (storedXp & 0xFFFFL);
                case DATA_XP_HI -> (int) ((storedXp >>> 16) & 0xFFFFL);
                case DATA_RADIUS -> TrapWave2Logic.xpRadius(getMotorCount())
                        + UpgradeMath.areaRangeBonus(getSpecialCount());
                case DATA_BOTTLED -> bottledTotal;
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

    public XpCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XP_COLLECTOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, XpCollectorBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int motors = getMotorCount();
        int batteries = getBatteryCount();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);
        int collectCost = TrapWave2Logic.xpCollectEnergy(batteries, ownerLvl);
        int bottleCost = TrapWave2Logic.xpBottleEnergy(batteries, ownerLvl);
        int scanInterval = TrapWave2Logic.xpScanInterval(motors, ownerLvl);
        int bottleTime = TrapWave2Logic.xpBottleTime(motors, ownerLvl);

        if (energy.getEnergyStored() < Math.min(collectCost, bottleCost)) {
            status = MachineStatus.NO_ENERGY;
            bottleProgress = 0;
            activeGlowTicks = 0;
            updateLit(level, state, false);
            return;
        }

        if (activeGlowTicks > 0) {
            activeGlowTicks--;
        }

        // 1. Collect XP orbs within 10..20 block radius
        boolean workedThisTick = false;
        scanTicks++;
        if (scanTicks >= scanInterval) {
            scanTicks = 0;
            if (storedXp < TrapWave2Logic.XP_MAX_STORAGE && energy.getEnergyStored() >= collectCost) {
                int radius = TrapWave2Logic.xpRadius(motors) + UpgradeMath.areaRangeBonus(getSpecialCount());
                double cx = worldPosition.getX() + 0.5D;
                double cy = worldPosition.getY() + 0.5D;
                double cz = worldPosition.getZ() + 0.5D;
                AABB box = new AABB(cx - radius, cy - radius, cz - radius,
                        cx + radius, cy + radius, cz + radius);
                double rSq = (double) radius * (double) radius;
                List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box,
                        orb -> orb.isAlive() && !orb.isRemoved() && orb.getValue() > 0
                                && orb.distanceToSqr(cx, cy, cz) <= rSq);
                int particles = 0;
                for (ExperienceOrb orb : orbs) {
                    if (storedXp >= TrapWave2Logic.XP_MAX_STORAGE
                            || energy.getEnergyStored() < collectCost) {
                        break;
                    }
                    long room = TrapWave2Logic.XP_MAX_STORAGE - storedXp;
                    int val = orb.getValue();
                    int take = (int) Math.min(val, room);
                    if (take <= 0) {
                        continue;
                    }
                    storedXp += take;
                    energy.consume(collectCost);
                    workedThisTick = true;
                    if (take >= val) {
                        orb.discard();
                    } else {
                        orb.value = val - take;
                    }
                }
            }
        }

        // 2. Auto-bottle Glass Bottles + 7 XP -> Experience Bottle
        ItemStack bottleIn = inventory.getStackInSlot(INPUT_SLOT);
        boolean outputFull = false;
        if (bottleIn.is(Items.GLASS_BOTTLE) && storedXp >= TrapWave2Logic.XP_PER_BOTTLE) {
            if (!canInsertBottledResult()) {
                bottleProgress = 0;
                outputFull = true;
            } else if (energy.getEnergyStored() >= bottleCost) {
                bottleProgress++;
                workedThisTick = true;
                if (bottleProgress >= bottleTime) {
                    bottleProgress = 0;
                    inventory.extractItem(INPUT_SLOT, 1, false);
                    insertBottledResult();
                    storedXp -= TrapWave2Logic.XP_PER_BOTTLE;
                    energy.consume(bottleCost);
                    bottledTotal++;
                }
            }
        } else {
            bottleProgress = 0;
        }

        if (workedThisTick) {
            activeGlowTicks = 25;
            status = outputFull ? MachineStatus.OUTPUT_FULL : MachineStatus.WORKING;
            workTracker.recordActiveTick(level, MACHINE_ID);
            updateLit(level, state, true);
            setChanged();
        } else if (outputFull) {
            status = MachineStatus.OUTPUT_FULL;
            activeGlowTicks = 0;
            updateLit(level, state, false);
        } else {
            status = activeGlowTicks > 0 ? MachineStatus.WORKING : MachineStatus.IDLE;
            // Phase 113: Always-on standby emerald orb whenever powered & not redstone-disabled
            updateLit(level, state, !level.hasNeighborSignal(worldPosition));
        }
    }

    /** Withdraws stored XP into {@code player} for {@code +1 Lv}, {@code +10 Lv}, or {@code All}. */
    public int withdrawToPlayer(ServerPlayer player, int action) {
        if (storedXp <= 0L) {
            return 0;
        }
        int grant = TrapWave2Logic.computeXpWithdrawal(
                storedXp, player.experienceLevel, player.experienceProgress, action);
        if (grant <= 0) {
            return 0;
        }
        storedXp -= grant;
        player.giveExperiencePoints(grant);
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.55F, 1.05F);
        }
        setChanged();
        return grant;
    }

    private boolean canInsertBottledResult() {
        ItemStack probe = new ItemStack(Items.EXPERIENCE_BOTTLE);
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT; i++) {
            ItemStack cur = inventory.getStackInSlot(i);
            if (cur.isEmpty() || (ItemStack.isSameItemSameComponents(cur, probe)
                    && cur.getCount() < cur.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    private void insertBottledResult() {
        ItemStack rem = new ItemStack(Items.EXPERIENCE_BOTTLE);
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && !rem.isEmpty(); i++) {
            rem = inventory.insertItem(i, rem, false);
        }
    }

    public int currentBottleTime() {
        int lvl = (level instanceof ServerLevel sl) ? workTracker.ownerLevel(sl, MACHINE_ID) : 0;
        return TrapWave2Logic.xpBottleTime(getMotorCount(), lvl);
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(XpCollectorBlock.LIT) && state.getValue(XpCollectorBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(XpCollectorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(TrapWave2Logic.XP_BASE_CAPACITY, getBatteryCount());
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
        return com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory.FIELD;
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
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public long getStoredXp() {
        return storedXp;
    }

    public int getBottledTotal() {
        return bottledTotal;
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.xp_collector");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new XpCollectorMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putLong(TAG_STORED_XP, storedXp);
        tag.putInt(TAG_PROGRESS, bottleProgress);
        tag.putInt(TAG_BOTTLED, bottledTotal);
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
        storedXp = Math.max(0L, Math.min(TrapWave2Logic.XP_MAX_STORAGE, tag.getLong(TAG_STORED_XP)));
        bottleProgress = Math.max(0, tag.getInt(TAG_PROGRESS));
        bottledTotal = Math.max(0, tag.getInt(TAG_BOTTLED));
        workTracker.load(tag);
    }

    private static final class XpInventory extends ItemStackHandler {
        private final XpCollectorBlockEntity owner;

        XpInventory(XpCollectorBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == INPUT_SLOT) {
                return stack.is(Items.GLASS_BOTTLE);
            }
            if (slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_COUNT) {
                return stack.is(Items.EXPERIENCE_BOTTLE);
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

    /** External automation view: inserts glass bottles into slot 0, extracts bottles from 1..3 only. */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return (slot >= 0 && slot < SLOT_COUNT) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != INPUT_SLOT || !stack.is(Items.GLASS_BOTTLE)) {
                return stack;
            }
            return inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < OUTPUT_START || slot >= OUTPUT_START + OUTPUT_COUNT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return (slot >= 0 && slot < SLOT_COUNT) ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT && stack.is(Items.GLASS_BOTTLE);
        }
    }
}
