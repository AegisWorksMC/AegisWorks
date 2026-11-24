package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.block.ItemPipeBlock;
import com.industrialcore.machine.LogisticsRules;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.PipeConnectionRules;
import com.industrialcore.machine.PipeTier;
import com.industrialcore.machine.VisualSyncRules;
import com.industrialcore.client.PortArmCache;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Server-authoritative item pipe with a real, visible transit time.
 *
 * <p>A pipe holds at most one stack. It pulls a stack from an adjacent inventory, chooses the
 * exit side, spends {@link PipeTier#travelTicks()} ticks carrying it through the tube, and only
 * then hands it to the chosen inventory or to the next pipe. The hand-off is an ownership move:
 * the stack object leaves this pipe before the next pipe stores it, so it can never exist twice.
 * A blocked destination keeps the stack at the exit and retries; nothing is dropped or voided.</p>
 *
 * <p>Routing order and the hop limit are the Phase 4 rules ({@link LogisticsRules}). Neighbour
 * capabilities are cached per direction and invalidated by a block-state change or chunk unload.
 * The client receives only display data (stack, entry/exit side, progress, blocked) when it
 * changes; the authoritative buffer is never sent and the client never moves an item.</p>
 */
public class ItemPipeBlockEntity extends BlockEntity {
    private static final Direction[] DIRECTIONS = Direction.values();

    private static final String TAG_BUFFER = "Buffer";
    private static final String TAG_FROM = "From";
    private static final String TAG_TO = "To";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_LAST_INSERT = "LastInsert";
    private static final String TAG_HOPS = "Hops";
    /** Six bytes of {@link PipeConnectionMode} ordinals, indexed by direction. */
    private static final String TAG_MODES = "Modes";

    private static final String TAG_VISUAL = "Visual";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_VISUAL_FROM = "From";
    private static final String TAG_VISUAL_TO = "To";
    private static final String TAG_VISUAL_PROGRESS = "Progress";
    private static final String TAG_VISUAL_BLOCKED = "Blocked";
    private static final String TAG_VISUAL_TRAVEL = "Travel";

    /** Short backoff after a blocked hand-off; a pipe of a busy line must not re-scan every tick. */
    private static final int BLOCKED_RETRY_TICKS = 5;

    private final PipeTier tier;

    /**
     * Transport mode of each face (Phase 10). Default {@link PipeConnectionMode#NONE}: placing a
     * pipe, or loading a pipe saved before this phase, never starts moving items by itself.
     */
    private final PipeConnectionMode[] modes = new PipeConnectionMode[6];
    /** Reused buffer for the pull candidates of one tick (avoids two arrays per tick). */
    private final int[] pullScratch = new int[PipeConnectionRules.DIRECTION_COUNT];
    /** Reused buffer for the rotated destination candidates (Phase 80). */
    private final int[] forwardScratch = new int[PipeConnectionRules.DIRECTION_COUNT - 1];

    /** Cached "neighbour is another item pipe" answers, one per face (Phase 80). */
    private final boolean[] pipeNeighbours = new boolean[PipeConnectionRules.DIRECTION_COUNT];
    private final boolean[] pipeNeighbourKnown = new boolean[PipeConnectionRules.DIRECTION_COUNT];
    /**
     * Rotating start of the destination search, used only to break ties between equally idle
     * sinks. The actual fairness comes from {@link #lastDelivery}.
     */
    private int destinationCursor;
    /**
     * Delivery clock: the tick order in which each face last received a stack. The destination
     * with the oldest stamp among the faces that accept the stack wins, which is exactly a
     * round-robin over the sinks that can take it, independent of their geometric order.
     */
    private final int[] lastDelivery = new int[PipeConnectionRules.DIRECTION_COUNT];
    private int deliveryClock;

    private ItemStack buffer = ItemStack.EMPTY;
    /** Ticks the stack has spent inside this pipe. */
    private int transitTicks;
    /** Total ticks this stack needs for this block (tier value, persisted for display). */
    private int travelTicks;
    @Nullable
    private Direction from;
    @Nullable
    private Direction to;
    @Nullable
    private Direction lastInsert;
    private int hops;
    private int retryCooldown;
    private boolean blocked;

    private int tickCounter;
    private boolean visualDirty;
    private int lastVisualSyncTick = -1;
    private int visualSyncCount;
    @Nullable
    private ClientVisual clientVisual;

    /**
     * Faces whose neighbour could not be read because its chunk is not loaded, as a bit mask by
     * {@link Direction#get3DDataValue()}. Only these faces are re-checked, and only until the
     * chunk they point at is loaded, so an idle pipe never walks all six neighbours per tick.
     */
    private int unresolvedFaces;

    private final BlockState[] neighbourStates = new BlockState[6];
    private final IItemHandler[] neighbourHandlers = new IItemHandler[6];
    private int resolvedDirections;
    private int knownHandlers;

    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PIPE.get(), pos, state);
        this.tier = state.getBlock() instanceof ItemPipeBlock pipeBlock ? pipeBlock.tier() : PipeTier.BASIC;
        this.travelTicks = this.tier.travelTicks();
        Arrays.fill(this.modes, PipeConnectionMode.NONE);
    }

    // ------------------------------------------------------------------
    // Connection modes (Phase 10)
    // ------------------------------------------------------------------

    /** Transport mode of one face; never null. */
    public PipeConnectionMode getMode(Direction direction) {
        if (direction == null) {
            return PipeConnectionMode.NONE;
        }
        return modes[direction.get3DDataValue()];
    }

    /**
     * Sets the mode of one face. Only meaningful on the server; a client-side call is ignored so
     * the authoritative configuration can never be changed by display code.
     *
     * @return true when the mode actually changed
     */
    public boolean setMode(Direction direction, PipeConnectionMode mode) {
        if (direction == null || mode == null || level == null || level.isClientSide) {
            return false;
        }
        int index = direction.get3DDataValue();
        if (modes[index] == mode) {
            return false;
        }
        modes[index] = mode;
        // A side that stops being a path must not keep a stack aimed at it.
        if (mode == PipeConnectionMode.NONE) {
            if (from == direction) {
                from = null;
            }
            if (to == direction) {
                to = null;
            }
        }
        retryCooldown = 0;
        visualDirty = true;
        setChanged();
        // The face markers are drawn by the block entity renderer from this array, which travels
        // in the display tag (see getUpdateTag). No block state is touched: keeping the mode out
        // of the state space is what keeps a pipe at 64 states instead of 262,144 (Phase 22
        // start-up regression). This also means a wrench change does not rebuild the chunk mesh.
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** Copy of the six modes in direction order (diagnostics, GUI, tests). */
    public PipeConnectionMode[] getModes() {
        return modes.clone();
    }

    /**
     * Mode this face actually transports with (Phase 80).
     *
     * <p>An unconfigured face that meets another item pipe is open ({@code NORMAL}) without any
     * wrench click; every other unconfigured face stays closed. The declared mode is never
     * changed by this, so the saved configuration and the client markers keep showing exactly
     * what the player set.</p>
     */
    public PipeConnectionMode effectiveMode(Direction direction) {
        if (direction == null) {
            return PipeConnectionMode.NONE;
        }
        if (level instanceof ServerLevel serverLevel) {
            return effectiveMode(serverLevel, direction);
        }
        return getMode(direction);
    }

    private PipeConnectionMode effectiveMode(ServerLevel level, Direction direction) {
        return PipeConnectionRules.effective(getMode(direction), isPipeNeighbour(level, direction));
    }

    /**
     * True when the neighbour on this face is another item pipe. Cached per face and invalidated by
     * {@link #invalidateNeighbourCache()} (a neighbour change or a chunk unload), so a busy pipe
     * does not read six block states per tick.
     */
    private boolean isPipeNeighbour(ServerLevel level, Direction direction) {
        int index = direction.get3DDataValue();
        if (pipeNeighbourKnown[index]) {
            return pipeNeighbours[index];
        }
        BlockPos target = worldPosition.relative(direction);
        if (!level.isLoaded(target)) {
            // Do not cache an unloaded face: it may become a pipe once its chunk loads.
            return false;
        }
        pipeNeighbours[index] = level.getBlockState(target).getBlock() instanceof ItemPipeBlock;
        pipeNeighbourKnown[index] = true;
        return pipeNeighbours[index];
    }

    /** True when at least one face can transport items. */
    public boolean isConfigured() {
        for (PipeConnectionMode mode : modes) {
            if (mode.isTransport()) {
                return true;
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ItemPipeBlockEntity pipe) {
        if (level instanceof ServerLevel serverLevel) {
            pipe.tick(serverLevel);
        }
    }

    /** Records a face whose neighbour chunk is not loaded yet; resolved on a later tick. */
    public static void markUnresolved(LevelAccessor level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) {
            pipe.unresolvedFaces |= 1 << direction.get3DDataValue();
        }
    }

    /**
     * Re-checks only the faces that were left unresolved while their chunk was unloaded. Runs
     * before the idle early return because an idle pipe is exactly the case that needs it.
     */
    private void resolveUnresolved(ServerLevel level) {
        if (unresolvedFaces == 0) {
            return;
        }
        BlockState state = getBlockState();
        int pending = unresolvedFaces;
        for (Direction direction : DIRECTIONS) {
            int bit = 1 << direction.get3DDataValue();
            if ((pending & bit) == 0) {
                continue;
            }
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!level.hasChunk(SectionPos.blockToSectionCoord(neighborPos.getX()),
                    SectionPos.blockToSectionCoord(neighborPos.getZ()))) {
                continue;
            }
            ItemPipeBlock.Link link = ItemPipeBlock.linkTo(level, worldPosition, direction,
                    level.getBlockState(neighborPos));
            unresolvedFaces &= ~bit;
            if (link == ItemPipeBlock.Link.UNLOADED) {
                unresolvedFaces |= bit;
                continue;
            }
            BooleanProperty property = ItemPipeBlock.CONNECTION_BY_DIRECTION.get(direction);
            boolean linked = link == ItemPipeBlock.Link.LINKED;
            if (state.getValue(property) != linked) {
                // Shape/connection only: no neighbour notification, same as neighborChanged.
                state = state.setValue(property, linked);
                level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    private void tick(ServerLevel level) {
        resolveUnresolved(level);
        // Empty and no inventory around: nothing can be pulled and no neighbour can accept a
        // stack from an empty pipe until a block changes (which invalidates this cache).
        if (buffer.isEmpty() && retryCooldown == 0 && resolvedDirections == 6 && knownHandlers == 0) {
            return;
        }
        tickCounter++;

        if (buffer.isEmpty()) {
            tryPull(level);
            if (buffer.isEmpty()) {
                maybeSyncVisual(level);
                return;
            }
        }

        if (transitTicks < travelTicks) {
            transitTicks++;
            // Persist the progress so a chunk save in the middle of a route restores the exact
            // position without triggering comparator neighbor scans on every tick.
            level.blockEntityChanged(worldPosition);
            if (transitTicks >= travelTicks) {
                blocked = false;
                visualDirty = true;
            }
            maybeSyncVisual(level);
            return;
        }

        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        boolean wasBlocked = blocked;
        Direction previousTo = to;
        if (deliver(level)) {
            blocked = false;
            if (wasBlocked) {
                visualDirty = true;
            }
        } else {
            blocked = true;
            retryCooldown = BLOCKED_RETRY_TICKS;
            // Only a change of state or exit side needs a packet; a pipe that stays blocked
            // must not re-broadcast the same snapshot every retry.
            if (!wasBlocked || previousTo != to) {
                visualDirty = true;
            }
        }
        level.blockEntityChanged(worldPosition);
        maybeSyncVisual(level);
    }

    private void tryPull(ServerLevel level) {
        int amount = tier.itemsPerTransfer(Config.pipeItemsPerTick());
        // Phase 10: only a face explicitly set to PULL may extract from its inventory. A pipe that
        // was just placed (all faces NONE) therefore moves nothing.
        int candidates = PipeConnectionRules.collectPullCandidates(
                modes, LogisticsRules.priorityOrder(), pullScratch);
        for (int candidate = 0; candidate < candidates; candidate++) {
            Direction direction = DIRECTIONS[pullScratch[candidate]];
            if (direction == lastInsert) {
                // Never pull back what this pipe just delivered.
                continue;
            }
            IItemHandler handler = handlerAt(level, direction);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack extracted = handler.extractItem(slot, amount, false);
                if (!extracted.isEmpty()) {
                    buffer = extracted;
                    from = direction;
                    hops = 0;
                    beginTransit(level);
                    return;
                }
            }
        }
    }

    /**
     * Picks the side the stack will leave through, without moving it.
     *
     * <p>Phase 80: the candidate order is rotated by {@link #destinationCursor}, so a pipe with
     * four equally valid sinks hands consecutive stacks to four different sides instead of
     * filling the first one. A sink that cannot take the stack (a full chest, a machine whose
     * input slot does not accept it, a busy pipe at the hop limit) is skipped by the simulated
     * insert, and the search simply continues with the next candidate: that is the automatic
     * reroute. Returning {@code null} means nothing can take the stack right now, which keeps it
     * inside the pipe instead of voiding it.</p>
     */
    @Nullable
    private Direction chooseDestination(ServerLevel level, @Nullable Direction arrival) {
        if (buffer.isEmpty()) {
            return null;
        }
        int fromIndex = arrival == null ? LogisticsRules.NO_DIRECTION : arrival.get3DDataValue();
        boolean mayForward = LogisticsRules.canForward(hops, Config.pipeMaxHops());
        int candidates = LogisticsRules.fillRotatedForwardOrder(fromIndex, destinationCursor,
                forwardScratch);
        Direction best = null;
        int bestStamp = Integer.MAX_VALUE;
        for (int candidate = 0; candidate < candidates; candidate++) {
            Direction direction = DIRECTIONS[forwardScratch[candidate]];
            if (!acceptsNow(level, direction, mayForward)) {
                continue;
            }
            int stamp = lastDelivery[direction.get3DDataValue()];
            if (stamp < bestStamp) {
                bestStamp = stamp;
                best = direction;
            }
        }
        if (best != null) {
            lastDelivery[best.get3DDataValue()] = ++deliveryClock;
            destinationCursor++;
        }
        return best;
    }

    /**
     * True when this face can take the held stack right now, without changing anything. A pipe
     * must be empty and still have hop budget; an inventory must accept at least one item through
     * the simulated insert. This is the check that makes a full chest or a refusing machine a
     * skipped candidate instead of a lost stack.
     */
    private boolean acceptsNow(ServerLevel level, Direction direction, boolean mayForward) {
        BlockPos target = worldPosition.relative(direction);
        if (!level.isLoaded(target)) {
            return false;
        }
        PipeConnectionMode mode = effectiveMode(level, direction);
        if (!PipeConnectionRules.canTraverse(mode)) {
            return false;
        }
        if (level.getBlockEntity(target) instanceof ItemPipeBlockEntity next) {
            return mayForward && next.canAccept(buffer)
                    && PipeConnectionRules.canHandOff(mode,
                    next.effectiveMode(level, direction.getOpposite()));
        }
        // An inventory is only a destination when this face is explicitly PUSH.
        if (!PipeConnectionRules.canPush(mode)) {
            return false;
        }
        IItemHandler handler = handlerAt(level, direction);
        if (handler == null) {
            return false;
        }
        // A simulated insert must not change anything, so the buffer itself can be passed instead
        // of a copy: this runs for every candidate while routing a stack.
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, buffer, true);
        return remainder.getCount() < buffer.getCount();
    }

    private boolean deliver(ServerLevel level) {
        if (to != null && tryDeliverTo(level, to)) {
            return true;
        }
        // The planned side may have filled up or started refusing this stack. Re-evaluate and
        // reroute, but never back to the side the stack came from (the rotated order already
        // excludes it).
        Direction alternative = chooseDestination(level, from);
        if (alternative != null) {
            to = alternative;
            if (tryDeliverTo(level, alternative)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryDeliverTo(ServerLevel level, Direction direction) {
        BlockPos target = worldPosition.relative(direction);
        if (!level.isLoaded(target)) {
            return false;
        }
        PipeConnectionMode mode = effectiveMode(level, direction);
        if (!PipeConnectionRules.canTraverse(mode)) {
            return false;
        }
        if (level.getBlockEntity(target) instanceof ItemPipeBlockEntity next) {
            if (!LogisticsRules.canForward(hops, Config.pipeMaxHops())) {
                return false;
            }
            if (!PipeConnectionRules.canHandOff(mode,
                    next.effectiveMode(level, direction.getOpposite()))) {
                return false;
            }
            if (!next.accept(buffer, direction.getOpposite(), hops + 1)) {
                return false;
            }
            lastInsert = direction;
            clearBuffer();
            return true;
        }
        if (!PipeConnectionRules.canPush(mode)) {
            return false;
        }
        IItemHandler handler = handlerAt(level, direction);
        if (handler == null) {
            return false;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, buffer.copy(), false);
        if (remainder.getCount() == buffer.getCount()) {
            return false;
        }
        lastInsert = direction;
        if (remainder.isEmpty()) {
            clearBuffer();
        } else {
            // A partial insert is kept inside the pipe; the remainders stays at the exit.
            buffer = remainder;
            transitTicks = travelTicks;
            visualDirty = true;
            setChanged();
        }
        return true;
    }

    private void clearBuffer() {
        buffer = ItemStack.EMPTY;
        from = null;
        to = null;
        transitTicks = 0;
        travelTicks = tier.travelTicks();
        hops = 0;
        retryCooldown = 0;
        blocked = false;
        visualDirty = true;
        setChanged();
    }

    /** True when this pipe is empty and could take the stack (no side effects). */
    public boolean canAccept(ItemStack stack) {
        return buffer.isEmpty() && !stack.isEmpty();
    }

    /**
     * Takes ownership of a forwarded stack. Returns false when this pipe is already busy.
     *
     * <p>Public since Phase 19: an adjacent item distributor hands its buffered stack over the
     * same path a neighbouring pipe uses, so the whole route stays visible in the tube. The
     * arrival face must be set to a transporting mode; a pipe that was never configured keeps
     * refusing everything, exactly as before.</p>
     */
    public boolean accept(ItemStack stack, Direction arrival, int newHops) {
        if (!buffer.isEmpty() || stack.isEmpty()) {
            return false;
        }
        PipeConnectionMode arrivalMode = level instanceof ServerLevel serverLevel
                ? effectiveMode(serverLevel, arrival) : getMode(arrival);
        if (!PipeConnectionRules.canTraverse(arrivalMode)) {
            // The receiving face was switched off after the sender picked this side.
            return false;
        }
        buffer = stack;
        from = arrival;
        hops = Math.max(0, newHops);
        beginTransit(level instanceof ServerLevel serverLevel ? serverLevel : null);
        return true;
    }

    private void beginTransit(@Nullable ServerLevel level) {
        transitTicks = 0;
        travelTicks = tier.travelTicks();
        to = level == null ? null : chooseDestination(level, from);
        blocked = false;
        retryCooldown = 0;
        visualDirty = true;
        setChanged();
    }

    private int lastFromIndex() {
        return from == null ? LogisticsRules.NO_DIRECTION : from.get3DDataValue();
    }

    private IItemHandler handlerAt(ServerLevel level, Direction direction) {
        return resolve(level, direction, direction.get3DDataValue());
    }

    private IItemHandler resolve(ServerLevel level, Direction direction, int index) {
        BlockPos target = worldPosition.relative(direction);
        if (!level.isLoaded(target)) {
            markUnresolved(index);
            return null;
        }
        BlockState state = level.getBlockState(target);
        if (state == neighbourStates[index] && neighbourStates[index] != null) {
            return neighbourHandlers[index];
        }
        IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, target, direction.getOpposite());
        boolean firstResolution = neighbourStates[index] == null;
        if (neighbourHandlers[index] == null && handler != null) {
            knownHandlers++;
        } else if (neighbourHandlers[index] != null && handler == null) {
            knownHandlers = Math.max(0, knownHandlers - 1);
        }
        if (firstResolution) {
            resolvedDirections++;
        }
        neighbourStates[index] = state;
        neighbourHandlers[index] = handler;
        return handler;
    }

    private void markUnresolved(int index) {
        if (neighbourStates[index] != null) {
            neighbourStates[index] = null;
            resolvedDirections = Math.max(0, resolvedDirections - 1);
        }
        if (neighbourHandlers[index] != null) {
            neighbourHandlers[index] = null;
            knownHandlers = Math.max(0, knownHandlers - 1);
        }
    }

    /** Called when a neighbour changed; the next tick re-resolves the capability. */
    public void invalidateNeighbourCache() {
        if (armCache != null) {
            armCache.invalidate();
        }
        Arrays.fill(neighbourStates, null);
        Arrays.fill(neighbourHandlers, null);
        Arrays.fill(pipeNeighbourKnown, false);
        resolvedDirections = 0;
        knownHandlers = 0;
    }

    /** Faces still waiting for their neighbour's chunk to load (diagnostics and tests). */
    public int unresolvedFaces() {
        return unresolvedFaces;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        invalidateNeighbourCache();
        clientVisual = null;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNeighbourCache();
        clientVisual = null;
    }

    /** Port anchors of the neighbouring machines, resolved on demand by the arm renderer. */
    private PortArmCache armCache;

    /** Client-only cache of the port anchors around this pipe; resolves nothing until asked. */
    public PortArmCache armCache() {
        if (armCache == null) {
            armCache = new PortArmCache();
        }
        return armCache;
    }

    // ------------------------------------------------------------------
    // Visual sync
    // ------------------------------------------------------------------

    private void maybeSyncVisual(ServerLevel level) {
        if (!VisualSyncRules.dueForSync(tickCounter, lastVisualSyncTick, visualDirty, !buffer.isEmpty(),
                Config.logisticsVisualMinIntervalTicks(), 0)) {
            return;
        }
        lastVisualSyncTick = tickCounter;
        visualDirty = false;
        visualSyncCount++;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------
    // Test / diagnostic accessors
    // ------------------------------------------------------------------

    public ItemStack getBuffer() {
        return buffer.copy();
    }

    public int getBufferCount() {
        return buffer.getCount();
    }

    public int getHops() {
        return hops;
    }

    public int getKnownHandlers() {
        return knownHandlers;
    }

    public PipeTier getTier() {
        return tier;
    }

    /** 0..1 progress through this pipe block; 1 also means "waiting at the exit". */
    public float getProgress() {
        return travelTicks <= 0 ? 1.0F : Math.min(1.0F, transitTicks / (float) travelTicks);
    }

    public boolean isBlocked() {
        return blocked;
    }

    @Nullable
    public Direction getFrom() {
        return from;
    }

    @Nullable
    public Direction getTo() {
        return to;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!buffer.isEmpty()) {
            Block.popResource(level, worldPosition, buffer.copy());
        }
        clearBuffer();
        from = null;
        lastInsert = null;
        hops = 0;
        visualDirty = true;
        setChanged();
    }

    /** Number of visual updates this pipe has broadcast (diagnostic / GameTest). */
    public int getVisualSyncCount() {
        return visualSyncCount;
    }

    @Nullable
    public ClientVisual getClientVisual() {
        return clientVisual;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!buffer.isEmpty()) {
            tag.put(TAG_BUFFER, buffer.save(registries));
        }
        if (from != null) {
            tag.putInt(TAG_FROM, from.get3DDataValue());
        }
        if (to != null) {
            tag.putInt(TAG_TO, to.get3DDataValue());
        }
        tag.putFloat(TAG_PROGRESS, getProgress());
        if (lastInsert != null) {
            tag.putInt(TAG_LAST_INSERT, lastInsert.get3DDataValue());
        }
        tag.putInt(TAG_HOPS, hops);
        byte[] encoded = new byte[modes.length];
        for (int index = 0; index < modes.length; index++) {
            encoded[index] = (byte) modes[index].ordinal();
        }
        tag.putByteArray(TAG_MODES, encoded);
    }

    /**
     * Display-only tag. The authoritative {@code Buffer} tag is never sent; the client receives
     * one stack plus the entry/exit sides and the progress needed to draw it.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag visual = new CompoundTag();
        if (!buffer.isEmpty()) {
            visual.put(TAG_STACK, buffer.save(registries));
        }
        visual.putInt(TAG_VISUAL_FROM, from == null ? -1 : from.get3DDataValue());
        visual.putInt(TAG_VISUAL_TO, to == null ? -1 : to.get3DDataValue());
        visual.putFloat(TAG_VISUAL_PROGRESS, getProgress());
        visual.putBoolean(TAG_VISUAL_BLOCKED, blocked);
        visual.putInt(TAG_VISUAL_TRAVEL, Math.max(1, travelTicks));
        byte[] encoded = new byte[modes.length];
        for (int index = 0; index < modes.length; index++) {
            encoded[index] = (byte) modes[index].ordinal();
        }
        visual.putByteArray(TAG_MODES, encoded);
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_VISUAL, visual);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (level != null && level.isClientSide) {
            clientVisual = ClientVisual.parse(tag.getCompound(TAG_VISUAL), registries, level.getGameTime());
            return;
        }
        // Older worlds have no mode data: every face stays NONE, so their pipes do not suddenly
        // start transporting after the update.
        Arrays.fill(modes, PipeConnectionMode.NONE);
        byte[] encoded = tag.getByteArray(TAG_MODES);
        for (int index = 0; index < Math.min(encoded.length, modes.length); index++) {
            modes[index] = PipeConnectionMode.byIndex(encoded[index] & 0xFF);
        }
        buffer = tag.contains(TAG_BUFFER)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_BUFFER))
                : ItemStack.EMPTY;
        from = tag.contains(TAG_FROM) ? directionFromTag(tag.getInt(TAG_FROM)) : null;
        to = tag.contains(TAG_TO) ? directionFromTag(tag.getInt(TAG_TO)) : null;
        lastInsert = tag.contains(TAG_LAST_INSERT) ? directionFromTag(tag.getInt(TAG_LAST_INSERT)) : null;
        hops = Math.max(0, tag.getInt(TAG_HOPS));
        travelTicks = tier.travelTicks();
        float savedProgress = tag.getFloat(TAG_PROGRESS);
        if (!Float.isFinite(savedProgress)) {
            savedProgress = 0.0F;
        }
        transitTicks = Math.max(0, Math.min(travelTicks,
                Math.round(Math.max(0.0F, Math.min(1.0F, savedProgress)) * travelTicks)));
        retryCooldown = 0;
        blocked = false;
        invalidateNeighbourCache();
        visualDirty = true;
        lastVisualSyncTick = -1;
    }

    /**
     * Immutable client-side snapshot: one stack, the entry and exit sides, and the server progress.
     * The renderer extrapolates from {@code receivedTick} without any client-side item logic.
     */
    public static final class ClientVisual {
        private static final ClientVisual EMPTY = new ClientVisual(ItemStack.EMPTY, null, null, 0.0F,
                false, 1, 0L, new PipeConnectionMode[6]);

        private final ItemStack stack;
        @Nullable
        private final Direction from;
        @Nullable
        private final Direction to;
        private final float progress;
        private final boolean blocked;
        private final int travelTicks;
        private final long receivedTick;
        private final PipeConnectionMode[] modes;

        private ClientVisual(ItemStack stack, @Nullable Direction from, @Nullable Direction to,
                             float progress, boolean blocked, int travelTicks, long receivedTick,
                             PipeConnectionMode[] modes) {
            this.stack = stack;
            this.from = from;
            this.to = to;
            this.progress = progress;
            this.blocked = blocked;
            this.travelTicks = travelTicks;
            this.receivedTick = receivedTick;
            this.modes = modes;
        }

        /** Mode of one face as last synced from the server. Never null. */
        public PipeConnectionMode mode(Direction direction) {
            if (direction == null) {
                return PipeConnectionMode.NONE;
            }
            int index = direction.get3DDataValue();
            if (index < 0 || index >= modes.length) {
                return PipeConnectionMode.NONE;
            }
            PipeConnectionMode mode = modes[index];
            return mode == null ? PipeConnectionMode.NONE : mode;
        }

        public boolean isEmpty() {
            return stack.isEmpty();
        }

        public ItemStack stack() {
            return stack;
        }

        @Nullable
        public Direction from() {
            return from;
        }

        @Nullable
        public Direction to() {
            return to;
        }

        /** Server progress at the moment the update was sent (0..1). */
        public float progress() {
            return progress;
        }

        public boolean blocked() {
            return blocked;
        }

        public int travelTicks() {
            return travelTicks;
        }

        public long receivedTick() {
            return receivedTick;
        }

        /**
         * Parses a display tag. Public so the face markers can be tested headless: the modes must
         * survive even when the pipe currently carries no stack (that is the normal state of a
         * configured pipe, and the empty visual used to drop them).
         */
        public static ClientVisual parse(CompoundTag visual, HolderLookup.Provider registries, long receivedTick) {
            if (visual.isEmpty()) {
                return EMPTY;
            }
            PipeConnectionMode[] modes = new PipeConnectionMode[6];
            java.util.Arrays.fill(modes, PipeConnectionMode.NONE);
            byte[] encoded = visual.getByteArray(TAG_MODES);
            for (int index = 0; index < Math.min(encoded.length, modes.length); index++) {
                modes[index] = PipeConnectionMode.byIndex(encoded[index] & 0xFF);
            }
            ItemStack stack = visual.contains(TAG_STACK)
                    ? ItemStack.parseOptional(registries, visual.getCompound(TAG_STACK))
                    : ItemStack.EMPTY;
            if (stack.isEmpty()) {
                // No travelling item, but the face modes still have to reach the renderer.
                return new ClientVisual(ItemStack.EMPTY, null, null, 0.0F, false, 1, receivedTick, modes);
            }
            float progress = visual.getFloat(TAG_VISUAL_PROGRESS);
            if (!Float.isFinite(progress)) {
                progress = 0.0F;
            }
            int travel = Math.max(1, visual.getInt(TAG_VISUAL_TRAVEL));
            return new ClientVisual(stack,
                    directionFromTag(visual.getInt(TAG_VISUAL_FROM)),
                    directionFromTag(visual.getInt(TAG_VISUAL_TO)),
                    Math.max(0.0F, Math.min(1.0F, progress)),
                    visual.getBoolean(TAG_VISUAL_BLOCKED),
                    travel,
                    receivedTick,
                    modes);
        }
    }

    @Nullable
    private static Direction directionFromTag(int index) {
        Direction[] values = Direction.values();
        return index < 0 || index >= values.length ? null : values[index];
    }
}
