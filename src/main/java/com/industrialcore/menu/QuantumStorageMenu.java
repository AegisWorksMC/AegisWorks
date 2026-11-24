package com.industrialcore.menu;

import com.industrialcore.blockentity.QuantumStorageBlockEntity;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.FluidKey;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.ItemKey;
import com.industrialcore.item.PortableQuantumTerminalItem;
import com.industrialcore.item.StorageCoreItem;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.QuantumFreqActionPayload;
import com.industrialcore.network.QuantumStorageActionPayload;
import com.industrialcore.network.QuantumStorageSyncPayload;
import com.industrialcore.network.QuantumStorageSyncPayload.ChannelSummary;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import com.industrialcore.storage.QuantumJeiTransferLogic;
import com.industrialcore.storage.QuantumJeiTransferLogic.MutableSlot;
import com.industrialcore.storage.QuantumNetLogic;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import com.industrialcore.storage.QuantumNetworkData;
import com.industrialcore.storage.QuantumStorageData;
import com.industrialcore.storage.QuantumStorageLayout;
import com.industrialcore.storage.StorageCoreTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Phase 89 / 96: Menu of the Quantum Storage Core &amp; Portable Quantum Terminal.
 *
 * <p>Supports both Block Core mode (local or frequency-linked) and Portable Terminal mode
 * (cross-dimension remote access with distance-free {@link #stillValid}, terminal slot lock, and
 * full JEI one-tap crafting recipe transfer).</p>
 */
public class QuantumStorageMenu extends AbstractContainerMenu {
    public static final int CORE_START = 0;
    public static final int CORE_COUNT = StorageCoreTier.CORE_SLOT_COUNT; // 8
    public static final int CRAFT_RESULT_SLOT = CORE_START + CORE_COUNT; // 8
    public static final int CRAFT_GRID_START = CRAFT_RESULT_SLOT + 1;    // 9
    public static final int CRAFT_GRID_COUNT = 9;                        // 9..17
    public static final int PLAYER_INV_START = CRAFT_GRID_START + CRAFT_GRID_COUNT; // 18

    private static final BigInteger MAX_INT_BIG = BigInteger.valueOf(Integer.MAX_VALUE);

    private final BlockPos pos;
    private final boolean portableMode;
    private final int portableSlotIndex; // 0..8 hotbar slot, or -1 for offhand
    private String portableChannelId;
    private final Player player;
    @Nullable
    private final QuantumStorageBlockEntity blockEntity;
    private final CraftingContainer craftMatrix = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer craftResult = new ResultContainer();

    // Server-side revision tracking for change-only live S2C sync
    private long lastSyncedRevision = Long.MIN_VALUE;

    // Client-side synced snapshot & collapsible side dock visibility
    private int clientMaxTypes = StorageCoreTier.BASE_MAX_TYPES;
    private BigInteger clientMaxPerType = StorageCoreTier.BASE_MAX_PER_TYPE;
    private String clientChannelId = "";
    private String clientChannelName = "";
    private List<QuantumStorageSyncPayload.ItemEntry> clientItems = List.of();
    private List<QuantumStorageSyncPayload.FluidEntry> clientFluids = List.of();
    private List<ChannelSummary> clientChannels = List.of();
    private long clientSyncCounter = 0L;
    private boolean coreDockVisible = true;

    /** Dist-safe client audio feedback callback (set by ClientSetup). */
    public static Runnable clientCraftSound;
    public static Runnable clientLockedSound;

    public QuantumStorageMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, readBuf(buf));
    }

    private record OpenArgs(boolean portable, BlockPos pos, int slotIdx, String channelId) {}

    private static OpenArgs readBuf(RegistryFriendlyByteBuf buf) {
        boolean portable = buf.readBoolean();
        if (portable) {
            int slot = buf.readVarInt();
            String chId = buf.readUtf(64);
            return new OpenArgs(true, BlockPos.ZERO, slot, chId);
        }
        return new OpenArgs(false, buf.readBlockPos(), -2, "");
    }

    private QuantumStorageMenu(int containerId, Inventory playerInventory, OpenArgs args) {
        this(containerId, playerInventory, args.pos, args.portable, args.slotIdx, args.channelId,
                args.portable ? null : resolve(playerInventory, args.pos));
    }

    public QuantumStorageMenu(int containerId, Inventory playerInventory,
                              QuantumStorageBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), false, -2, "", blockEntity);
    }

    public QuantumStorageMenu(int containerId, Inventory playerInventory,
                              int portableSlotIndex, String portableChannelId) {
        this(containerId, playerInventory, BlockPos.ZERO, true, portableSlotIndex, portableChannelId, null);
    }

    private QuantumStorageMenu(int containerId, Inventory playerInventory, BlockPos pos,
                               boolean portableMode, int portableSlotIndex, String portableChannelId,
                               @Nullable QuantumStorageBlockEntity blockEntity) {
        super(ModMenus.QUANTUM_STORAGE_CORE.get(), containerId);
        this.pos = pos;
        this.portableMode = portableMode;
        this.portableSlotIndex = portableSlotIndex;
        this.portableChannelId = portableChannelId == null ? "" : portableChannelId;
        this.player = playerInventory.player;
        this.blockEntity = blockEntity;
        if (portableMode) {
            this.coreDockVisible = false;
        }

        IItemHandler coreInv = blockEntity != null
                ? blockEntity.getCoreInventory()
                : new ItemStackHandler(CORE_COUNT);

        // 0..7: Storage core slots (inactive in portable mode or when Core dock is hidden)
        for (int i = 0; i < CORE_COUNT; i++) {
            Rect r = QuantumStorageLayout.coreSlot(i);
            final int coreIndex = i;
            this.addSlot(new SlotItemHandler(coreInv, i, r.x(), r.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (portableMode || !(stack.getItem() instanceof StorageCoreItem)) {
                        return false;
                    }
                    return canChangeCoreSlot(coreIndex, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    return 1;
                }

                @Override
                public boolean mayPickup(Player p) {
                    if (portableMode) {
                        return false;
                    }
                    if (!canRemoveCoreSlot(coreIndex)) {
                        notifyCoreLocked(p);
                        return false;
                    }
                    return true;
                }

                @Override
                public boolean isActive() {
                    return !portableMode && (!player.level().isClientSide || coreDockVisible);
                }
            });
        }

        // 8: Built-in 3x3 crafting result slot with auto-refill
        Rect outRect = QuantumStorageLayout.CRAFT_OUTPUT;
        this.addSlot(new AutoRefillResultSlot(playerInventory.player, craftMatrix, craftResult,
                0, outRect.x(), outRect.y()));

        // 9..17: 3x3 crafting grid
        for (int i = 0; i < CRAFT_GRID_COUNT; i++) {
            Rect r = QuantumStorageLayout.craftSlot(i);
            this.addSlot(new Slot(craftMatrix, i, r.x(), r.y()));
        }

        // 18..53: Player inventory + hotbar (locks active portable terminal slot against accidental drop/deposit)
        int pIdx = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Rect r = QuantumStorageLayout.playerSlot(pIdx++);
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, r.x(), r.y()));
            }
        }
        for (int col = 0; col < 9; col++) {
            Rect r = QuantumStorageLayout.playerSlot(pIdx++);
            final boolean lockedTerminal = portableMode && col == portableSlotIndex;
            if (lockedTerminal) {
                this.addSlot(new Slot(playerInventory, col, r.x(), r.y()) {
                    @Override
                    public boolean mayPickup(Player p) {
                        return false;
                    }

                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            } else {
                this.addSlot(new Slot(playerInventory, col, r.x(), r.y()));
            }
        }
    }

    @Nullable
    private static QuantumStorageBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof QuantumStorageBlockEntity be
                ? be : null;
    }

    // ------------------------------------------------------------------
    // Unified Storage Backend (Block Core local/network OR Portable Terminal network)
    // ------------------------------------------------------------------

    @Nullable
    private QuantumStorageData<ItemKey, FluidKey> activeStorageServer() {
        if (!portableMode) {
            return blockEntity != null ? blockEntity.getStorageData() : null;
        }
        QuantumChannel<ItemKey, FluidKey> ch = activePortableChannel();
        return ch != null ? ch.storage() : null;
    }

    @Nullable
    private QuantumChannel<ItemKey, FluidKey> activePortableChannel() {
        if (!portableMode || portableChannelId.isEmpty() || !(player.level() instanceof ServerLevel sl)) {
            return null;
        }
        QuantumChannel<ItemKey, FluidKey> ch = QuantumNetworkData.get(sl).getChannel(portableChannelId);
        if (ch == null || !ch.canAccess(player.getUUID().toString())) {
            return null;
        }
        return ch;
    }

    private ItemStack getPortableTerminalStack() {
        if (!portableMode) {
            return ItemStack.EMPTY;
        }
        if (portableSlotIndex == -1) {
            return player.getOffhandItem();
        }
        if (portableSlotIndex >= 0 && portableSlotIndex < 9) {
            return player.getInventory().getItem(portableSlotIndex);
        }
        return ItemStack.EMPTY;
    }

    private boolean consumePortableActionEnergy(ServerPlayer sp) {
        if (!portableMode) {
            return true;
        }
        ItemStack term = getPortableTerminalStack();
        if (term.isEmpty() || !(term.getItem() instanceof PortableQuantumTerminalItem)) {
            return false;
        }
        if (!PortableQuantumTerminalItem.consumeEnergy(
                term, QuantumNetLogic.PORTABLE_ACTION_COST, sp.isCreative())) {
            sp.displayClientMessage(Component.translatable("gui.industrialcore.machine.status.no_energy")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    private void bumpActiveRevision() {
        if (!portableMode) {
            if (blockEntity != null) {
                blockEntity.bumpRevision();
            }
            return;
        }
        if (player.level() instanceof ServerLevel sl) {
            QuantumNetworkData net = QuantumNetworkData.get(sl);
            QuantumChannel<ItemKey, FluidKey> ch = portableChannelId.isEmpty()
                    ? null : net.getChannel(portableChannelId);
            if (ch != null) {
                ch.bumpRevision();
            }
            net.markMutated();
        }
    }

    private long computeServerRevision() {
        if (!portableMode) {
            return blockEntity != null ? blockEntity.getRevision() : 0L;
        }
        if (player.level() instanceof ServerLevel sl) {
            QuantumNetworkData net = QuantumNetworkData.get(sl);
            QuantumChannel<ItemKey, FluidKey> ch = activePortableChannel();
            long chRev = ch != null ? ch.revision() : 0L;
            return net.globalRevision() * 17L + chRev * 31L + portableChannelId.hashCode();
        }
        return 0L;
    }

    private ItemStack insertIntoActive(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!portableMode) {
            return blockEntity != null ? blockEntity.insertItemStack(stack, simulate) : stack;
        }
        QuantumStorageData<ItemKey, FluidKey> d = activeStorageServer();
        if (d == null) {
            return stack;
        }
        BigInteger accepted = d.insertItem(ItemKey.of(stack), BigInteger.valueOf(stack.getCount()), simulate);
        int taken = accepted.min(BigInteger.valueOf(stack.getCount())).intValue();
        if (!simulate && taken > 0) {
            bumpActiveRevision();
        }
        return taken >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - taken);
    }

    private ItemStack extractFromActive(ItemKey key, int requested, boolean simulate) {
        if (key == null || requested <= 0) {
            return ItemStack.EMPTY;
        }
        if (!portableMode) {
            return blockEntity != null ? blockEntity.extractItemStack(key, requested, simulate) : ItemStack.EMPTY;
        }
        QuantumStorageData<ItemKey, FluidKey> d = activeStorageServer();
        if (d == null) {
            return ItemStack.EMPTY;
        }
        BigInteger extracted = d.extractItem(key, BigInteger.valueOf(requested), simulate);
        int count = extracted.min(MAX_INT_BIG).intValue();
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            bumpActiveRevision();
        }
        return key.toStack(count);
    }

    private FluidStack extractFluidFromActive(FluidKey key, int requestedMb, boolean simulate) {
        if (key == null || requestedMb <= 0) {
            return FluidStack.EMPTY;
        }
        if (!portableMode) {
            return blockEntity != null
                    ? blockEntity.extractFluidStack(key, requestedMb, simulate) : FluidStack.EMPTY;
        }
        QuantumStorageData<ItemKey, FluidKey> d = activeStorageServer();
        if (d == null) {
            return FluidStack.EMPTY;
        }
        BigInteger extracted = d.extractFluid(key, BigInteger.valueOf(requestedMb), simulate);
        int amt = extracted.min(MAX_INT_BIG).intValue();
        if (amt <= 0) {
            return FluidStack.EMPTY;
        }
        if (!simulate) {
            bumpActiveRevision();
        }
        return key.toStack(amt);
    }

    @Nullable
    private IFluidHandler activeFluidHandler() {
        if (!portableMode) {
            return blockEntity != null ? blockEntity.getFluidHandler() : null;
        }
        QuantumStorageData<ItemKey, FluidKey> d = activeStorageServer();
        if (d == null) {
            return null;
        }
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return d.fluidTypeCount() + 1;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                List<FluidKey> keys = new ArrayList<>(d.fluidsView().keySet());
                if (tank < 0 || tank >= keys.size()) {
                    return FluidStack.EMPTY;
                }
                FluidKey k = keys.get(tank);
                return k.toStack(d.getFluidAmount(k).min(MAX_INT_BIG).intValue());
            }

            @Override
            public int getTankCapacity(int tank) {
                return d.maxPerType().min(MAX_INT_BIG).intValue();
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
                BigInteger acc = d.insertFluid(FluidKey.of(resource),
                        BigInteger.valueOf(resource.getAmount()), action.simulate());
                if (!action.simulate() && acc.signum() > 0) {
                    bumpActiveRevision();
                }
                return acc.min(MAX_INT_BIG).intValue();
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return resource.isEmpty() ? FluidStack.EMPTY
                        : extractFluidFromActive(FluidKey.of(resource), resource.getAmount(), action.simulate());
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                List<FluidKey> keys = new ArrayList<>(d.fluidsView().keySet());
                return keys.isEmpty() ? FluidStack.EMPTY
                        : extractFluidFromActive(keys.get(0), maxDrain, action.simulate());
            }
        };
    }

    // ------------------------------------------------------------------
    // Core removal guard (block core local mode)
    // ------------------------------------------------------------------

    public boolean canRemoveCoreSlot(int slotIndex) {
        return canChangeCoreSlot(slotIndex, ItemStack.EMPTY);
    }

    public boolean canChangeCoreSlot(int slotIndex, ItemStack replacement) {
        if (portableMode) {
            return false;
        }
        if (blockEntity != null && !player.level().isClientSide) {
            return blockEntity.canReplaceCore(slotIndex, replacement);
        }
        if (!clientChannelId.isEmpty()) {
            return true;
        }
        StorageCoreTier[] preview = new StorageCoreTier[CORE_COUNT];
        for (int i = 0; i < CORE_COUNT; i++) {
            ItemStack s = i == slotIndex ? replacement : this.slots.get(CORE_START + i).getItem();
            preview[i] = s.getItem() instanceof StorageCoreItem c ? c.tier() : null;
        }
        int nextTypes = StorageCoreTier.totalMaxTypes(preview);
        int used = clientItems.size() + clientFluids.size();
        if (used > nextTypes) {
            return false;
        }
        BigInteger nextMax = StorageCoreTier.totalMaxPerType(preview);
        for (QuantumStorageSyncPayload.ItemEntry e : clientItems) {
            if (e.count().compareTo(nextMax) > 0) {
                return false;
            }
        }
        for (QuantumStorageSyncPayload.FluidEntry e : clientFluids) {
            if (e.amount().compareTo(nextMax) > 0) {
                return false;
            }
        }
        return true;
    }

    private void notifyCoreLocked(Player p) {
        if (p instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("gui.industrialcore.storage.core_locked"), true);
        } else {
            Runnable r = clientLockedSound;
            if (r != null) {
                r.run();
            }
        }
    }

    // ------------------------------------------------------------------
    // Server -> Client synchronization (real-time across cores & portable terminals)
    // ------------------------------------------------------------------

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer sp && (portableMode || blockEntity != null)) {
            long rev = computeServerRevision();
            if (rev != lastSyncedRevision) {
                lastSyncedRevision = rev;
                syncToPlayer(sp);
            }
        }
    }

    public void forceSyncNow() {
        if (player instanceof ServerPlayer sp && (portableMode || blockEntity != null)) {
            lastSyncedRevision = computeServerRevision();
            syncToPlayer(sp);
        }
    }

    private void syncToPlayer(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel sl)) {
            return;
        }
        QuantumNetworkData net = QuantumNetworkData.get(sl);
        String viewerUuid = sp.getUUID().toString();

        String activeId = "";
        String activeName = "";
        QuantumStorageData<ItemKey, FluidKey> d = activeStorageServer();
        if (!portableMode && blockEntity != null) {
            QuantumChannel<ItemKey, FluidKey> ch = blockEntity.getActiveChannel();
            if (ch != null) {
                activeId = ch.channelId();
                activeName = ch.name();
            }
        } else if (portableMode) {
            QuantumChannel<ItemKey, FluidKey> ch = activePortableChannel();
            if (ch != null) {
                activeId = ch.channelId();
                activeName = ch.name();
            }
        }

        List<QuantumStorageSyncPayload.ItemEntry> itemEntries = new ArrayList<>();
        List<QuantumStorageSyncPayload.FluidEntry> fluidEntries = new ArrayList<>();
        int maxTypes = StorageCoreTier.BASE_MAX_TYPES;
        String maxPerType = StorageCoreTier.BASE_MAX_PER_TYPE.toString();
        if (d != null) {
            maxTypes = d.maxTypes();
            maxPerType = d.maxPerType().toString();
            for (Map.Entry<ItemKey, BigInteger> e : d.itemsView().entrySet()) {
                if (e.getValue().signum() > 0) {
                    itemEntries.add(new QuantumStorageSyncPayload.ItemEntry(
                            e.getKey().toStack(1), e.getValue()));
                }
            }
            for (Map.Entry<FluidKey, BigInteger> e : d.fluidsView().entrySet()) {
                if (e.getValue().signum() > 0) {
                    fluidEntries.add(new QuantumStorageSyncPayload.FluidEntry(
                            e.getKey().toStack(1), e.getValue()));
                }
            }
        }

        List<QuantumChannel<ItemKey, FluidKey>> visible = net.visibleChannelsFor(viewerUuid);
        List<ChannelSummary> summaries = new ArrayList<>(visible.size());
        for (QuantumChannel<ItemKey, FluidKey> ch : visible) {
            summaries.add(new ChannelSummary(
                    ch.channelId(), ch.name(), ch.isPublic(), ch.isOwner(viewerUuid),
                    ch.storage().usedTypes(), ch.storage().maxTypes()));
        }

        PacketDistributor.sendToPlayer(sp, new QuantumStorageSyncPayload(
                pos, portableMode, maxTypes, maxPerType, activeId, activeName,
                itemEntries, fluidEntries, summaries));
    }

    public void applyClientSync(QuantumStorageSyncPayload payload) {
        if (payload.portable() != this.portableMode) {
            return;
        }
        if (!this.portableMode && !payload.pos().equals(this.pos)) {
            return;
        }
        this.clientMaxTypes = payload.maxTypes();
        try {
            this.clientMaxPerType = new BigInteger(payload.maxPerType());
        } catch (NumberFormatException ex) {
            this.clientMaxPerType = StorageCoreTier.BASE_MAX_PER_TYPE;
        }
        this.clientChannelId = payload.activeChannelId();
        this.clientChannelName = payload.activeChannelName();
        this.clientItems = List.copyOf(payload.items());
        this.clientFluids = List.copyOf(payload.fluids());
        this.clientChannels = List.copyOf(payload.channels());
        this.clientSyncCounter++;
    }

    // ------------------------------------------------------------------
    // Server-side Frequency Action Handler (Phase 96)
    // ------------------------------------------------------------------

    public void handleFreqAction(ServerPlayer sp, QuantumFreqActionPayload payload) {
        if (!(sp.level() instanceof ServerLevel sl) || !stillValid(sp)) {
            return;
        }
        if (payload.portable() != this.portableMode) {
            return;
        }
        if (!this.portableMode && (blockEntity == null || !payload.pos().equals(this.pos))) {
            return;
        }
        QuantumNetworkData net = QuantumNetworkData.get(sl);
        String viewerUuid = sp.getUUID().toString();

        switch (payload.action()) {
            case QuantumFreqActionPayload.ACTION_CREATE -> {
                QuantumChannel<ItemKey, FluidKey> created = net.createChannel(
                        payload.value(), viewerUuid, payload.flag());
                connectToChannel(sp, created);
            }
            case QuantumFreqActionPayload.ACTION_CONNECT -> {
                QuantumChannel<ItemKey, FluidKey> target = net.getChannel(payload.value());
                if (target != null && target.canAccess(viewerUuid)) {
                    connectToChannel(sp, target);
                }
            }
            case QuantumFreqActionPayload.ACTION_DISCONNECT -> {
                if (!portableMode && blockEntity != null) {
                    blockEntity.setChannelId("");
                } else if (portableMode) {
                    portableChannelId = "";
                    ItemStack term = getPortableTerminalStack();
                    if (term.getItem() instanceof PortableQuantumTerminalItem) {
                        PortableQuantumTerminalItem.setChannel(term, "", "");
                    }
                }
            }
            case QuantumFreqActionPayload.ACTION_DELETE -> {
                if (net.deleteChannel(payload.value(), viewerUuid)) {
                    if (!portableMode && blockEntity != null
                            && payload.value().equals(blockEntity.getChannelId())) {
                        blockEntity.setChannelId("");
                    } else if (portableMode && payload.value().equals(portableChannelId)) {
                        portableChannelId = "";
                        ItemStack term = getPortableTerminalStack();
                        if (term.getItem() instanceof PortableQuantumTerminalItem) {
                            PortableQuantumTerminalItem.setChannel(term, "", "");
                        }
                    }
                }
            }
            case QuantumFreqActionPayload.ACTION_TOGGLE_PUBLIC ->
                    net.togglePublic(payload.value(), viewerUuid);
            default -> {}
        }
        forceSyncNow();
    }

    private void connectToChannel(ServerPlayer sp, QuantumChannel<ItemKey, FluidKey> ch) {
        if (!portableMode && blockEntity != null) {
            blockEntity.setChannelId(ch.channelId());
        } else if (portableMode) {
            portableChannelId = ch.channelId();
            ItemStack term = getPortableTerminalStack();
            if (term.getItem() instanceof PortableQuantumTerminalItem) {
                PortableQuantumTerminalItem.setChannel(term, ch.channelId(), ch.name());
            }
        }
    }

    // ------------------------------------------------------------------
    // Server-side storage action handler (called from ModNetwork)
    // ------------------------------------------------------------------

    public void handleAction(ServerPlayer sp, QuantumStorageActionPayload payload) {
        if (!portableMode && (blockEntity == null || !payload.pos().equals(this.pos))) {
            return;
        }
        if (activeStorageServer() == null) {
            forceSyncNow();
            return;
        }
        if (!consumePortableActionEnergy(sp)) {
            return;
        }
        switch (payload.action()) {
            case QuantumStorageActionPayload.ACTION_EXTRACT_ITEM -> handleExtractItem(sp, payload);
            case QuantumStorageActionPayload.ACTION_DEPOSIT_CARRIED -> handleDepositCarried(sp, payload.mode());
            case QuantumStorageActionPayload.ACTION_INTERACT_FLUID -> handleInteractFluid(sp, payload.targetFluid());
            case QuantumStorageActionPayload.ACTION_CLEAR_CRAFT -> clearCraftGridToStorage();
            case QuantumStorageActionPayload.ACTION_DEPOSIT_MATCHING -> depositPlayerInventory(sp);
            default -> {}
        }
        forceSyncNow();
    }

    private void handleExtractItem(ServerPlayer sp, QuantumStorageActionPayload payload) {
        if (payload.targetItem().isEmpty()) {
            return;
        }
        ItemKey key = ItemKey.of(payload.targetItem());
        int maxStack = payload.targetItem().getMaxStackSize();
        int mode = payload.mode();

        if (mode == QuantumStorageActionPayload.MODE_SHIFT_TO_INV) {
            ItemStack extracted = extractFromActive(key, maxStack, false);
            if (!extracted.isEmpty()) {
                if (!this.moveItemStackTo(extracted, PLAYER_INV_START, this.slots.size(), true)
                        || !extracted.isEmpty()) {
                    insertIntoActive(extracted, false);
                }
            }
            return;
        }

        if (mode == QuantumStorageActionPayload.MODE_PULL_ONE_TO_INV) {
            ItemStack carried = this.getCarried();
            if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, payload.targetItem())
                    && carried.getCount() < carried.getMaxStackSize()) {
                ItemStack pulled = extractFromActive(key, 1, false);
                if (!pulled.isEmpty()) {
                    carried.grow(pulled.getCount());
                    this.setCarried(carried);
                }
                return;
            }
            ItemStack pulled = extractFromActive(key, 1, false);
            if (!pulled.isEmpty()) {
                if (!this.moveItemStackTo(pulled, PLAYER_INV_START, this.slots.size(), true)
                        || !pulled.isEmpty()) {
                    insertIntoActive(pulled, false);
                }
            }
            return;
        }

        if (mode == QuantumStorageActionPayload.MODE_PUSH_ONE_FROM_INV) {
            ItemStack carried = this.getCarried();
            if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, payload.targetItem())) {
                ItemStack slice = carried.copyWithCount(1);
                ItemStack rem = insertIntoActive(slice, false);
                if (rem.isEmpty()) {
                    carried.shrink(1);
                    this.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                }
                return;
            }
            Inventory inv = sp.getInventory();
            for (int i = 0; i < 36; i++) {
                if (portableMode && i == portableSlotIndex) {
                    continue;
                }
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, payload.targetItem())) {
                    ItemStack rem = insertIntoActive(s.copyWithCount(1), false);
                    if (rem.isEmpty()) {
                        s.shrink(1);
                        inv.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
                    }
                    return;
                }
            }
            return;
        }

        ItemStack carried = this.getCarried();
        int wanted = mode == QuantumStorageActionPayload.MODE_HALF_TO_CURSOR
                ? Math.max(1, maxStack / 2) : maxStack;
        if (carried.isEmpty()) {
            ItemStack extracted = extractFromActive(key, wanted, false);
            if (!extracted.isEmpty()) {
                this.setCarried(extracted);
            }
        } else if (ItemStack.isSameItemSameComponents(carried, payload.targetItem())) {
            int room = carried.getMaxStackSize() - carried.getCount();
            if (room > 0) {
                ItemStack extracted = extractFromActive(key, Math.min(wanted, room), false);
                if (!extracted.isEmpty()) {
                    carried.grow(extracted.getCount());
                    this.setCarried(carried);
                }
            }
        } else {
            handleDepositCarried(sp, 0);
        }
    }

    private void handleDepositCarried(ServerPlayer sp, int mode) {
        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        IFluidHandler fh = activeFluidHandler();
        if (fh != null) {
            FluidActionResult emptyRes = FluidUtil.tryEmptyContainer(
                    carried, fh, Integer.MAX_VALUE, sp, true);
            if (emptyRes.isSuccess()) {
                if (carried.getCount() == 1) {
                    this.setCarried(emptyRes.getResult());
                } else {
                    carried.shrink(1);
                    this.setCarried(carried);
                    if (!emptyRes.getResult().isEmpty() && !sp.getInventory().add(emptyRes.getResult())) {
                        sp.drop(emptyRes.getResult(), false);
                    }
                }
                return;
            }
        }
        int toDeposit = mode == 1 ? 1 : carried.getCount();
        ItemStack slice = carried.copyWithCount(toDeposit);
        ItemStack leftover = insertIntoActive(slice, false);
        int inserted = toDeposit - leftover.getCount();
        if (inserted > 0) {
            carried.shrink(inserted);
            this.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
    }

    private void handleInteractFluid(ServerPlayer sp, FluidStack targetFluid) {
        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        IFluidHandler fh = activeFluidHandler();
        if (fh != null) {
            FluidActionResult emptyRes = FluidUtil.tryEmptyContainer(
                    carried, fh, Integer.MAX_VALUE, sp, true);
            if (emptyRes.isSuccess()) {
                if (carried.getCount() == 1) {
                    this.setCarried(emptyRes.getResult());
                } else {
                    carried.shrink(1);
                    this.setCarried(carried);
                    if (!emptyRes.getResult().isEmpty() && !sp.getInventory().add(emptyRes.getResult())) {
                        sp.drop(emptyRes.getResult(), false);
                    }
                }
                return;
            }
        }
        if (carried.is(Items.BUCKET) && !targetFluid.isEmpty()) {
            FluidKey key = FluidKey.of(targetFluid);
            FluidStack sim = extractFluidFromActive(key, 1000, true);
            if (sim.getAmount() == 1000) {
                ItemStack filledBucket = FluidUtil.getFilledBucket(sim);
                if (!filledBucket.isEmpty()) {
                    extractFluidFromActive(key, 1000, false);
                    if (carried.getCount() == 1) {
                        this.setCarried(filledBucket);
                    } else {
                        carried.shrink(1);
                        this.setCarried(carried);
                        if (!sp.getInventory().add(filledBucket)) {
                            sp.drop(filledBucket, false);
                        }
                    }
                }
            }
        }
    }

    public void clearCraftGridToStorage() {
        if (activeStorageServer() == null) {
            return;
        }
        for (int i = 0; i < CRAFT_GRID_COUNT; i++) {
            ItemStack s = craftMatrix.getItem(i);
            if (!s.isEmpty()) {
                ItemStack rem = insertIntoActive(s, false);
                craftMatrix.setItem(i, rem);
            }
        }
        slotsChanged(craftMatrix);
    }

    private void depositPlayerInventory(ServerPlayer sp) {
        if (activeStorageServer() == null) {
            return;
        }
        Inventory inv = sp.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                ItemStack rem = insertIntoActive(s, false);
                inv.setItem(i, rem);
            }
        }
    }

    // ------------------------------------------------------------------
    // Server-Authoritative JEI One-Tap Recipe Transfer (Block Core + Portable!)
    // ------------------------------------------------------------------

    public boolean handleJeiTransfer(ServerPlayer sp, ResourceLocation recipeId, boolean maxTransfer) {
        QuantumStorageData<ItemKey, FluidKey> active = activeStorageServer();
        if (active == null || recipeId == null || !stillValid(sp)
                || !(sp.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!consumePortableActionEnergy(sp)) {
            return false;
        }
        Optional<RecipeHolder<?>> holderOpt = serverLevel.getServer().getRecipeManager().byKey(recipeId);
        if (holderOpt.isEmpty() || !(holderOpt.get().value() instanceof CraftingRecipe recipe)
                || recipe.isSpecial()) {
            return false;
        }

        List<Predicate<ItemKey>> matchers = buildCraftingMatchers(recipe);
        if (matchers == null) {
            return false;
        }

        List<MutableSlot<ItemKey>> gridSlots = new ArrayList<>(CRAFT_GRID_COUNT);
        for (int i = 0; i < CRAFT_GRID_COUNT; i++) {
            ItemStack s = craftMatrix.getItem(i);
            gridSlots.add(s.isEmpty()
                    ? new MutableSlot<>(null, 0)
                    : new MutableSlot<>(ItemKey.of(s), s.getCount()));
        }

        Inventory inv = sp.getInventory();
        List<MutableSlot<ItemKey>> playerSlots = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            if (portableMode && i == portableSlotIndex) {
                playerSlots.add(new MutableSlot<>(null, 0));
                continue;
            }
            ItemStack s = inv.getItem(i);
            playerSlots.add(s.isEmpty()
                    ? new MutableSlot<>(null, 0)
                    : new MutableSlot<>(ItemKey.of(s), s.getCount()));
        }

        QuantumJeiTransferLogic.TransferResult res = QuantumJeiTransferLogic.executeTransfer(
                matchers,
                active,
                gridSlots,
                playerSlots,
                k -> Math.max(1, k.toStack(1).getMaxStackSize()),
                maxTransfer);

        for (int i = 0; i < CRAFT_GRID_COUNT; i++) {
            MutableSlot<ItemKey> ms = gridSlots.get(i);
            craftMatrix.setItem(i, ms.isEmpty() ? ItemStack.EMPTY : ms.key.toStack(ms.count));
        }
        for (int i = 0; i < 36; i++) {
            if (portableMode && i == portableSlotIndex) {
                continue;
            }
            MutableSlot<ItemKey> ms = playerSlots.get(i);
            inv.setItem(i, ms.isEmpty() ? ItemStack.EMPTY : ms.key.toStack(ms.count));
        }

        bumpActiveRevision();
        updateCraftingResult();
        broadcastChanges();
        forceSyncNow();
        return res.success();
    }

    @Nullable
    private static List<Predicate<ItemKey>> buildCraftingMatchers(CraftingRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<Predicate<ItemKey>> matchers = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            matchers.add(null);
        }
        if (recipe instanceof ShapedRecipe shaped) {
            int w = shaped.getWidth();
            int h = shaped.getHeight();
            if (w <= 0 || w > 3 || h <= 0 || h > 3) {
                return null;
            }
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    int src = r * w + c;
                    if (src < ingredients.size()) {
                        Ingredient ing = ingredients.get(src);
                        if (!ing.isEmpty()) {
                            matchers.set(r * 3 + c, k -> ing.test(k.toStack(1)));
                        }
                    }
                }
            }
        } else {
            if (ingredients.size() > 9) {
                return null;
            }
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ing = ingredients.get(i);
                if (!ing.isEmpty()) {
                    matchers.set(i, k -> ing.test(k.toStack(1)));
                }
            }
        }
        return matchers;
    }

    // ------------------------------------------------------------------
    // Built-in 3x3 Crafting + Auto-Refill from Quantum Storage
    // ------------------------------------------------------------------

    @Override
    public void slotsChanged(Container container) {
        if (container == craftMatrix) {
            updateCraftingResult();
        }
        super.slotsChanged(container);
    }

    private void updateCraftingResult() {
        Level level = player.level();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer sp)) {
            return;
        }
        CraftingInput input = craftMatrix.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> found = serverLevel.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, serverLevel);
        if (found.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = found.get();
            if (craftResult.setRecipeUsed(serverLevel, sp, holder)) {
                ItemStack assembled = holder.value().assemble(input, serverLevel.registryAccess());
                if (assembled.isItemEnabled(serverLevel.enabledFeatures())) {
                    result = assembled;
                }
            }
        }
        craftResult.setItem(0, result);
        this.setRemoteSlot(CRAFT_RESULT_SLOT, result);
        sp.connection.send(new ClientboundContainerSetSlotPacket(
                this.containerId, this.incrementStateId(), CRAFT_RESULT_SLOT, result));
    }

    private final class AutoRefillResultSlot extends ResultSlot {
        AutoRefillResultSlot(Player player, CraftingContainer craftSlots, Container container,
                             int slot, int x, int y) {
            super(player, craftSlots, container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player p) {
            com.industrialcore.level.CraftUnlockLogic.CraftCheckResult check =
                    com.industrialcore.level.CraftUnlockGate.checkCraft(p, this.getItem());
            if (!check.allowed()) {
                com.industrialcore.level.CraftUnlockGate.notifyBlocked(p, check);
                return false;
            }
            return super.mayPickup(p);
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            com.industrialcore.level.CraftUnlockLogic.CraftCheckResult check =
                    com.industrialcore.level.CraftUnlockGate.checkCraft(taker, stack);
            if (!check.allowed()) {
                com.industrialcore.level.CraftUnlockGate.notifyBlocked(taker, check);
                return;
            }
            ItemStack[] beforeTemplates = new ItemStack[CRAFT_GRID_COUNT];
            int[] beforeCounts = new int[CRAFT_GRID_COUNT];
            for (int i = 0; i < CRAFT_GRID_COUNT; i++) {
                ItemStack s = craftMatrix.getItem(i);
                beforeTemplates[i] = s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1);
                beforeCounts[i] = s.getCount();
            }

            super.onTake(taker, stack);

            if (!taker.level().isClientSide && activeStorageServer() != null) {
                for (int i = 0; i < CRAFT_GRID_COUNT; i++) {
                    if (beforeTemplates[i].isEmpty()) {
                        continue;
                    }
                    ItemStack after = craftMatrix.getItem(i);
                    int missing = beforeCounts[i] - after.getCount();
                    if (missing <= 0) {
                        continue;
                    }
                    if (after.isEmpty()) {
                        ItemStack pulled = extractFromActive(ItemKey.of(beforeTemplates[i]), 1, false);
                        if (!pulled.isEmpty()) {
                            craftMatrix.setItem(i, pulled);
                        }
                    } else if (ItemStack.isSameItemSameComponents(after, beforeTemplates[i])
                            && after.getCount() < after.getMaxStackSize()) {
                        ItemStack pulled = extractFromActive(ItemKey.of(beforeTemplates[i]), 1, false);
                        if (!pulled.isEmpty()) {
                            after.grow(pulled.getCount());
                            craftMatrix.setItem(i, after);
                        }
                    }
                }
                updateCraftingResult();
                forceSyncNow();
            } else if (taker.level().isClientSide) {
                Runnable r = clientCraftSound;
                if (r != null) {
                    r.run();
                }
            }
        }
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (!p.level().isClientSide) {
            if (activeStorageServer() != null) {
                clearCraftGridToStorage();
            }
            this.clearContainer(p, craftMatrix);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != craftResult && super.canTakeItemForPickAll(stack, slot);
    }

    // ------------------------------------------------------------------
    // Shift-Click Handling (including safe capped bulk crafting & terminal lock)
    // ------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (portableMode && index == PLAYER_INV_START + 27 + portableSlotIndex && portableSlotIndex >= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == CRAFT_RESULT_SLOT) {
            if (!slot.mayPickup(p)) {
                return ItemStack.EMPTY;
            }
            ItemStack firstResult = stack.copy();
            int maxCrafts = Math.max(1, firstResult.getMaxStackSize() / Math.max(1, firstResult.getCount()));
            int crafted = 0;
            while (crafted < maxCrafts && slot.hasItem()) {
                ItemStack current = slot.getItem();
                if (!ItemStack.isSameItemSameComponents(current, firstResult)) {
                    break;
                }
                ItemStack copy = current.copy();
                if (!this.moveItemStackTo(current, PLAYER_INV_START, this.slots.size(), true)) {
                    break;
                }
                slot.onQuickCraft(current, copy);
                slot.onTake(p, copy);
                crafted++;
                if (current.getCount() == copy.getCount()) {
                    break;
                }
            }
            return ItemStack.EMPTY;
        } else if (index >= CORE_START && index < CORE_START + CORE_COUNT) {
            if (portableMode || !canRemoveCoreSlot(index - CORE_START)) {
                notifyCoreLocked(p);
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= CRAFT_GRID_START && index < PLAYER_INV_START) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!portableMode && stack.getItem() instanceof StorageCoreItem) {
                if (!this.moveItemStackTo(stack, CORE_START, CORE_START + CORE_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!p.level().isClientSide && activeStorageServer() != null) {
                ItemStack leftover = insertIntoActive(stack, false);
                if (leftover.getCount() == stack.getCount()) {
                    return ItemStack.EMPTY;
                }
                slot.setByPlayer(leftover);
                slot.setChanged();
                forceSyncNow();
                return original;
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(p, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player p) {
        if (portableMode) {
            if (!p.isAlive()) {
                return false;
            }
            ItemStack term = getPortableTerminalStack();
            return !term.isEmpty() && term.is(ModItems.PORTABLE_QUANTUM_TERMINAL.get());
        }
        if (this.blockEntity != null) {
            return !this.blockEntity.isRemoved()
                    && Vec3.atCenterOf(this.pos).distanceToSqr(p.position()) <= 64.0D;
        }
        return p.level().isClientSide
                && Vec3.atCenterOf(this.pos).distanceToSqr(p.position()) <= 64.0D;
    }

    // ------------------------------------------------------------------
    // Getters for Screen
    // ------------------------------------------------------------------

    public BlockPos getBlockPos() {
        return pos;
    }

    public boolean isPortableMode() {
        return portableMode;
    }

    @Nullable
    public QuantumStorageBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getClientMaxTypes() {
        return clientMaxTypes;
    }

    public BigInteger getClientMaxPerType() {
        return clientMaxPerType;
    }

    public String getClientChannelId() {
        return clientChannelId;
    }

    public String getClientChannelName() {
        return clientChannelName;
    }

    public List<QuantumStorageSyncPayload.ItemEntry> getClientItems() {
        return clientItems;
    }

    public List<QuantumStorageSyncPayload.FluidEntry> getClientFluids() {
        return clientFluids;
    }

    public List<ChannelSummary> getClientChannels() {
        return clientChannels;
    }

    public int getClientUsedTypes() {
        return clientItems.size() + clientFluids.size();
    }

    public long getClientSyncCounter() {
        return clientSyncCounter;
    }

    public void setCoreDockVisible(boolean visible) {
        this.coreDockVisible = !portableMode && visible;
    }

    public boolean isCoreDockVisible() {
        return !portableMode && this.coreDockVisible;
    }

    public int getInstalledCoreCount() {
        if (portableMode) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < CORE_COUNT; i++) {
            if (!this.slots.get(CORE_START + i).getItem().isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
