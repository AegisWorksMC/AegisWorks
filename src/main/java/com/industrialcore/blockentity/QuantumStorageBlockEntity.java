package com.industrialcore.blockentity;

import com.industrialcore.item.StorageCoreItem;
import com.industrialcore.menu.QuantumStorageMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import com.industrialcore.storage.QuantumNetworkData;
import com.industrialcore.storage.QuantumStorageData;
import com.industrialcore.storage.StorageCoreTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 89 / 96: Block entity for the Quantum Storage Core.
 *
 * <p>Operates seamlessly in either <b>Local Mode</b> (default, 100% compatible with Phase 89 local
 * BigInteger storage + 8 capacity core slots) or <b>Frequency Network Mode</b> (connected to a
 * shared {@link QuantumNetworkData} channel, contributing its 8 capacity core slots to the
 * network's aggregate capacity while keeping local items safely preserved inside the block).</p>
 */
public class QuantumStorageBlockEntity extends BlockEntity implements MenuProvider {
    public static final String TAG_STORAGE_ROOT = "QuantumStorage";
    public static final String TAG_ITEMS = "Items";
    public static final String TAG_FLUIDS = "Fluids";
    public static final String TAG_CORES = "Cores";
    public static final String TAG_CHANNEL_ID = "ChannelId";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_AMOUNT = "Amount";

    private static final BigInteger MAX_INT_BIG = BigInteger.valueOf(Integer.MAX_VALUE);

    public record ItemKey(Item item, DataComponentPatch patch) {
        public static ItemKey of(ItemStack stack) {
            return new ItemKey(stack.getItem(), stack.getComponentsPatch());
        }

        public ItemStack toStack(int count) {
            return new ItemStack(item.builtInRegistryHolder(), Math.max(1, count), patch);
        }
    }

    public record FluidKey(Fluid fluid, DataComponentPatch patch) {
        public static FluidKey of(FluidStack stack) {
            return new FluidKey(stack.getFluid(), stack.getComponentsPatch());
        }

        public FluidStack toStack(int amount) {
            return new FluidStack(fluid.builtInRegistryHolder(), Math.max(1, amount), patch);
        }
    }

    private final QuantumStorageData<ItemKey, FluidKey> localData = new QuantumStorageData<>();
    private final CoreItemHandler coreInventory = new CoreItemHandler(this);
    private final StorageItemHandler itemCapability = new StorageItemHandler();
    private final StorageFluidHandler fluidCapability = new StorageFluidHandler();
    private String channelId = "";
    private long revision = 1L;

    public QuantumStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_STORAGE_CORE.get(), pos, state);
    }

    /** Returns the active storage backend: connected frequency channel when linked, else local storage. */
    public QuantumStorageData<ItemKey, FluidKey> getStorageData() {
        QuantumChannel<ItemKey, FluidKey> ch = getActiveChannel();
        return ch != null ? ch.storage() : localData;
    }

    /** Always returns this block's own local storage (preserved intact even while linked to a frequency). */
    public QuantumStorageData<ItemKey, FluidKey> getLocalStorageData() {
        return localData;
    }

    @Nullable
    public QuantumChannel<ItemKey, FluidKey> getActiveChannel() {
        if (channelId.isEmpty() || !(level instanceof ServerLevel sl)) {
            return null;
        }
        return QuantumNetworkData.get(sl).getChannel(channelId);
    }

    public String getChannelId() {
        return channelId;
    }

    public String getActiveChannelName() {
        QuantumChannel<ItemKey, FluidKey> ch = getActiveChannel();
        return ch != null ? ch.name() : "";
    }

    public boolean isConnectedToChannel() {
        return getActiveChannel() != null;
    }

    public void setChannelId(@Nullable String nextChannelId) {
        String clean = nextChannelId == null ? "" : nextChannelId.trim();
        this.channelId = clean;
        if (level instanceof ServerLevel sl) {
            QuantumNetworkData net = QuantumNetworkData.get(sl);
            if (clean.isEmpty() || net.getChannel(clean) == null) {
                this.channelId = "";
                net.removeNode(sl, worldPosition);
            } else {
                net.updateNode(this.channelId, sl, worldPosition, localData.snapshotCores());
            }
        }
        bumpRevision();
    }

    public void onBlockRemoved(ServerLevel sl) {
        QuantumNetworkData.get(sl).removeNode(sl, worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl && !channelId.isEmpty()) {
            QuantumNetworkData net = QuantumNetworkData.get(sl);
            if (net.getChannel(channelId) != null) {
                net.updateNode(channelId, sl, worldPosition, localData.snapshotCores());
            } else {
                channelId = "";
            }
        }
    }

    public ItemStackHandler getCoreInventory() {
        return coreInventory;
    }

    public IItemHandler getItemHandler() {
        return itemCapability;
    }

    public IFluidHandler getFluidHandler() {
        return fluidCapability;
    }

    public long getRevision() {
        if (level instanceof ServerLevel sl) {
            QuantumNetworkData net = QuantumNetworkData.get(sl);
            QuantumChannel<ItemKey, FluidKey> ch = channelId.isEmpty() ? null : net.getChannel(channelId);
            long chRev = ch != null ? ch.revision() : 0L;
            return revision * 131L + net.globalRevision() * 17L + chRev * 31L;
        }
        return revision;
    }

    public void bumpRevision() {
        revision++;
        if (level instanceof ServerLevel sl) {
            QuantumNetworkData net = QuantumNetworkData.get(sl);
            QuantumChannel<ItemKey, FluidKey> ch = channelId.isEmpty() ? null : net.getChannel(channelId);
            if (ch != null) {
                ch.bumpRevision();
                net.markMutated();
            }
        }
        setChanged();
    }

    /** True when the block holds at least one local item, fluid, or installed storage core. */
    public boolean hasAnySavedContent() {
        if (localData.usedTypes() > 0) {
            return true;
        }
        for (int i = 0; i < StorageCoreTier.CORE_SLOT_COUNT; i++) {
            if (!coreInventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean canRemoveCore(int slot) {
        if (isConnectedToChannel()) {
            return true;
        }
        return localData.canRemoveCore(slot);
    }

    public boolean canReplaceCore(int slot, ItemStack newStack) {
        if (isConnectedToChannel()) {
            return true;
        }
        StorageCoreTier next = newStack.getItem() instanceof StorageCoreItem coreItem
                ? coreItem.tier() : null;
        return localData.canChangeCore(slot, next);
    }

    // ------------------------------------------------------------------
    // Item & fluid mutations (routed transparently to active backend)
    // ------------------------------------------------------------------

    public BigInteger insertItem(ItemStack stack, BigInteger amount, boolean simulate) {
        if (stack.isEmpty() || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger accepted = getStorageData().insertItem(ItemKey.of(stack), amount, simulate);
        if (!simulate && accepted.signum() > 0) {
            bumpRevision();
        }
        return accepted;
    }

    public ItemStack insertItemStack(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        BigInteger accepted = insertItem(stack, BigInteger.valueOf(stack.getCount()), simulate);
        int taken = accepted.min(BigInteger.valueOf(stack.getCount())).intValue();
        if (taken >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        return stack.copyWithCount(stack.getCount() - taken);
    }

    public ItemStack extractItemStack(ItemKey key, int requested, boolean simulate) {
        if (key == null || requested <= 0) {
            return ItemStack.EMPTY;
        }
        BigInteger extracted = getStorageData().extractItem(key, BigInteger.valueOf(requested), simulate);
        int count = extracted.min(MAX_INT_BIG).intValue();
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            bumpRevision();
        }
        return key.toStack(count);
    }

    public BigInteger insertFluid(FluidStack stack, BigInteger amountMb, boolean simulate) {
        if (stack.isEmpty() || amountMb.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger accepted = getStorageData().insertFluid(FluidKey.of(stack), amountMb, simulate);
        if (!simulate && accepted.signum() > 0) {
            bumpRevision();
        }
        return accepted;
    }

    public FluidStack extractFluidStack(FluidKey key, int requestedMb, boolean simulate) {
        if (key == null || requestedMb <= 0) {
            return FluidStack.EMPTY;
        }
        BigInteger extracted = getStorageData().extractFluid(key, BigInteger.valueOf(requestedMb), simulate);
        int amount = extracted.min(MAX_INT_BIG).intValue();
        if (amount <= 0) {
            return FluidStack.EMPTY;
        }
        if (!simulate) {
            bumpRevision();
        }
        return key.toStack(amount);
    }

    // ------------------------------------------------------------------
    // MenuProvider
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.quantum_storage_core");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new QuantumStorageMenu(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------
    // NBT Persistence + Drop-with-Contents serialization (local data intact)
    // ------------------------------------------------------------------

    public CompoundTag writeStorageTag(HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        ListTag itemList = new ListTag();
        for (Map.Entry<ItemKey, BigInteger> e : localData.itemsView().entrySet()) {
            if (e.getValue().signum() <= 0) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_STACK, e.getKey().toStack(1).saveOptional(registries));
            entry.putString(TAG_AMOUNT, e.getValue().toString());
            itemList.add(entry);
        }
        root.put(TAG_ITEMS, itemList);

        ListTag fluidList = new ListTag();
        for (Map.Entry<FluidKey, BigInteger> e : localData.fluidsView().entrySet()) {
            if (e.getValue().signum() <= 0) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_STACK, e.getKey().toStack(1).saveOptional(registries));
            entry.putString(TAG_AMOUNT, e.getValue().toString());
            fluidList.add(entry);
        }
        root.put(TAG_FLUIDS, fluidList);
        root.put(TAG_CORES, coreInventory.serializeNBT(registries));
        if (!channelId.isEmpty()) {
            root.putString(TAG_CHANNEL_ID, channelId);
        }
        return root;
    }

    public void readStorageTag(CompoundTag root, HolderLookup.Provider registries) {
        localData.clearAll();
        if (root.contains(TAG_CORES)) {
            coreInventory.beginLoad();
            try {
                coreInventory.deserializeNBT(registries, root.getCompound(TAG_CORES));
                if (coreInventory.getSlots() != StorageCoreTier.CORE_SLOT_COUNT) {
                    coreInventory.setSize(StorageCoreTier.CORE_SLOT_COUNT);
                }
            } finally {
                coreInventory.endLoad();
            }
        }
        syncCoreTiersFromInventory(false);

        ListTag itemList = root.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < itemList.size(); i++) {
            CompoundTag entry = itemList.getCompound(i);
            ItemStack proto = ItemStack.parseOptional(registries, entry.getCompound(TAG_STACK));
            BigInteger amt = parseBigInt(entry.getString(TAG_AMOUNT));
            if (!proto.isEmpty() && amt.signum() > 0) {
                localData.putItemUnchecked(ItemKey.of(proto), amt);
            }
        }

        ListTag fluidList = root.getList(TAG_FLUIDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidList.size(); i++) {
            CompoundTag entry = fluidList.getCompound(i);
            FluidStack proto = FluidStack.parseOptional(registries, entry.getCompound(TAG_STACK));
            BigInteger amt = parseBigInt(entry.getString(TAG_AMOUNT));
            if (!proto.isEmpty() && amt.signum() > 0) {
                localData.putFluidUnchecked(FluidKey.of(proto), amt);
            }
        }
        channelId = root.getString(TAG_CHANNEL_ID);
        revision++;
    }

    public void saveContentsToItem(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag storageTag = writeStorageTag(registries);
        storageTag.remove(TAG_CHANNEL_ID);
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.put(TAG_STORAGE_ROOT, storageTag));
    }

    public void loadContentsFromItem(ItemStack stack, HolderLookup.Provider registries) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null && cd.contains(TAG_STORAGE_ROOT)) {
            readStorageTag(cd.copyTag().getCompound(TAG_STORAGE_ROOT), registries);
            setChanged();
        }
    }

    private static BigInteger parseBigInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigInteger.ZERO;
        }
        try {
            BigInteger parsed = new BigInteger(raw.trim());
            return parsed.signum() > 0 ? parsed : BigInteger.ZERO;
        } catch (NumberFormatException ex) {
            return BigInteger.ZERO;
        }
    }

    private void syncCoreTiersFromInventory(boolean pushToNetwork) {
        for (int i = 0; i < StorageCoreTier.CORE_SLOT_COUNT; i++) {
            ItemStack s = coreInventory.getStackInSlot(i);
            StorageCoreTier tier = s.getItem() instanceof StorageCoreItem core ? core.tier() : null;
            localData.setCoreUnchecked(i, tier);
        }
        if (pushToNetwork && !channelId.isEmpty() && level instanceof ServerLevel sl) {
            QuantumNetworkData net = QuantumNetworkData.get(sl);
            if (net.getChannel(channelId) != null) {
                net.updateNode(channelId, sl, worldPosition, localData.snapshotCores());
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_STORAGE_ROOT, writeStorageTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide) {
            return;
        }
        if (tag.contains(TAG_STORAGE_ROOT)) {
            readStorageTag(tag.getCompound(TAG_STORAGE_ROOT), registries);
        }
    }

    // ------------------------------------------------------------------
    // Core Upgrade Inventory Handler (8 slots)
    // ------------------------------------------------------------------

    public static final class CoreItemHandler extends ItemStackHandler {
        private final QuantumStorageBlockEntity owner;
        private boolean loading;

        CoreItemHandler(QuantumStorageBlockEntity owner) {
            super(StorageCoreTier.CORE_SLOT_COUNT);
            this.owner = owner;
        }

        void beginLoad() {
            this.loading = true;
        }

        void endLoad() {
            this.loading = false;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof StorageCoreItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!owner.canRemoveCore(slot)) {
                return ItemStack.EMPTY;
            }
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (loading) {
                return;
            }
            owner.syncCoreTiersFromInventory(true);
            owner.bumpRevision();
        }
    }

    // ------------------------------------------------------------------
    // External Item Automation Capability (Pipes / Hoppers / Distributors)
    // ------------------------------------------------------------------

    private final class StorageItemHandler implements IItemHandler {
        private List<ItemKey> keys() {
            return new ArrayList<>(getStorageData().itemsView().keySet());
        }

        @Override
        public int getSlots() {
            return getStorageData().itemTypeCount() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<ItemKey> keys = keys();
            if (slot < 0 || slot >= keys.size()) {
                return ItemStack.EMPTY;
            }
            ItemKey key = keys.get(slot);
            BigInteger count = getStorageData().getItemCount(key);
            if (count.signum() <= 0) {
                return ItemStack.EMPTY;
            }
            int clamped = count.min(MAX_INT_BIG).intValue();
            return key.toStack(clamped);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= getSlots()) {
                return stack;
            }
            return QuantumStorageBlockEntity.this.insertItemStack(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            List<ItemKey> keys = keys();
            if (slot < 0 || slot >= keys.size()) {
                return ItemStack.EMPTY;
            }
            ItemKey key = keys.get(slot);
            int maxTake = Math.min(amount, key.toStack(1).getMaxStackSize());
            return QuantumStorageBlockEntity.this.extractItemStack(key, maxTake, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return getStorageData().maxPerType().min(MAX_INT_BIG).intValue();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // External Fluid Automation Capability (Fluid Pipes / Buckets)
    // ------------------------------------------------------------------

    private final class StorageFluidHandler implements IFluidHandler {
        private List<FluidKey> keys() {
            return new ArrayList<>(getStorageData().fluidsView().keySet());
        }

        @Override
        public int getTanks() {
            return getStorageData().fluidTypeCount() + 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            List<FluidKey> keys = keys();
            if (tank < 0 || tank >= keys.size()) {
                return FluidStack.EMPTY;
            }
            FluidKey key = keys.get(tank);
            BigInteger amt = getStorageData().getFluidAmount(key);
            if (amt.signum() <= 0) {
                return FluidStack.EMPTY;
            }
            return key.toStack(amt.min(MAX_INT_BIG).intValue());
        }

        @Override
        public int getTankCapacity(int tank) {
            return getStorageData().maxPerType().min(MAX_INT_BIG).intValue();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            BigInteger accepted = QuantumStorageBlockEntity.this.insertFluid(
                    resource, BigInteger.valueOf(resource.getAmount()), action.simulate());
            return accepted.min(MAX_INT_BIG).intValue();
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return QuantumStorageBlockEntity.this.extractFluidStack(
                    FluidKey.of(resource), resource.getAmount(), action.simulate());
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            List<FluidKey> keys = keys();
            if (keys.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return QuantumStorageBlockEntity.this.extractFluidStack(
                    keys.get(0), maxDrain, action.simulate());
        }
    }
}
