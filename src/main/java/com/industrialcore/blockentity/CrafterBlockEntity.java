package com.industrialcore.blockentity;

import com.industrialcore.Config;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.CrafterDistribution;
import com.industrialcore.machine.CrafterRecipePlan;
import com.industrialcore.machine.CrafterRecipeRules;
import com.industrialcore.machine.CrafterSpeed;
import com.industrialcore.machine.CrafterStatus;
import com.industrialcore.machine.FaceAwareItemHandler;
import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.CrafterMenu;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative auto crafter.
 *
 * <p>The machine keeps nine input slots and one output slot. It matches the selected vanilla
 * crafting recipe against the exact 3x3 arrangement (see {@link CrafterRecipePlan}): every pattern
 * cell must hold its own ingredient, and exactly those slots are consumed. It consumes FE per
 * crafting tick and only takes the ingredients when the result is actually inserted. A blocked
 * output, a broken shape, missing ingredients or missing power never destroys materials.</p>
 *
 * <p>The recipe id is the only setting. It is validated through the recipe manager every time a
 * craft is planned, so a data pack reload cannot leave the machine using a recipe object that no
 * longer exists.</p>
 */
public class CrafterBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOT = 9;
    /** Dedicated motor slot; GUI only, automation cannot insert or extract it. */
    public static final int UPGRADE_SLOT = 10;
    /** Phase 87: dedicated battery slot; GUI only, automation cannot insert or extract it. */
    public static final int BATTERY_SLOT = 11;
    public static final int SLOT_COUNT = 12;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_CRAFT_TIME = 4;
    public static final int DATA_SPEED_LEVEL = 5;
    public static final int DATA_MAX_SPEED_LEVEL = 6;
    public static final int DATA_COUNT = 7;

    /** Payload actions for a machine face (Phase 11.6). */
    // Shared with the GUI and the network handler (Phase 11.6); see MachineActions.
    public static final int FACE_ACTION_MODE = MachineActions.FACE_MODE;
    public static final int FACE_ACTION_SLOT = MachineActions.FACE_SLOT;
    public static final int FACE_ACTION_FILTER = MachineActions.FACE_FILTER;
    public static final int FACE_ACTION_ADD_FILTER_ITEM = MachineActions.FACE_ADD_ITEM;
    public static final int FACE_ACTION_CLEAR_FILTER = MachineActions.FACE_CLEAR;

    private static final String TAG_RECIPE = "Recipe";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_SPEED_LEVEL = "SpeedLevel";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_FACES = "Faces";

    private final CrafterItemHandler inventory = new CrafterItemHandler(this);
    private final AutomationHandler automationHandler = new AutomationHandler();
    private final ModEnergyStorage energy = new ModEnergyStorage(20_000, 500);

    /** Phase 11.3-11.5: per-face mode, target slot and item filter. */
    private final MachineFaceConfig faceConfig = new MachineFaceConfig();
    /** Cached per-side capability views; they only wrap the two shared handlers. */
    private final IItemHandler[] sideHandlers = new IItemHandler[6];
    /** Ingredient of each input slot for the selected recipe; rebuilt when the recipe changes. */
    private Ingredient[] recipeGrid = new Ingredient[INPUT_SLOTS];
    private String recipeGridId = "\u0000";

    /** Empty means no recipe. Stored as a string so it survives data pack reloads and NBT. */
    private String selectedRecipe = "";
    private int speedLevel;
    private int progress;
    private int retryCooldown;
    private CrafterStatus status = CrafterStatus.NO_RECIPE;
    private final MachineWorkTracker workTracker = new MachineWorkTracker();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATUS -> status.ordinal();
                case DATA_PROGRESS -> progress;
                case DATA_CRAFT_TIME -> getCraftTime();
                case DATA_SPEED_LEVEL -> speedLevel;
                case DATA_MAX_SPEED_LEVEL -> getMaxSpeedLevel();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server authoritative; the client menu keeps its own writable cache.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRAFTER.get(), pos, state);
        for (int side = 0; side < 6; side++) {
            sideHandlers[side] = new FaceAwareItemHandler(automationHandler, faceConfig,
                    Direction.from3DDataValue(side), this::insertAutomation,
                    slot -> slot >= 0 && slot < INPUT_SLOTS,
                    slot -> slot == OUTPUT_SLOT);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrafterBlockEntity crafter) {
        if (level instanceof ServerLevel serverLevel) {
            crafter.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        if (consumeUpgrade()) {
            setChanged();
        }
        int capacity = UpgradeMath.scaledCapacity(Config.crafterCapacity(), getBatteryCount());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            setChanged();
        }
        int maxReceive = Config.crafterMaxReceive();
        // Config reloads are picked up here without another field to keep in sync, but an
        // unchanged value is not rewritten every tick.
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }

        if (selectedRecipe.isEmpty()) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            setStatus(CrafterStatus.NO_RECIPE);
            return;
        }
        ensureRecipeGrid(level);
        if (progress > 0) {
            advanceCraft(level);
            return;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        startCraft(level);
    }

    /**
     * Caches the ingredient of every input slot for the selected recipe. Rebuilt only when the
     * recipe id changes (a data pack reload resets the id through the recipe manager lookup).
     */
    private void ensureRecipeGrid(ServerLevel level) {
        if (recipeGridId.equals(selectedRecipe)) {
            return;
        }
        CraftingRecipe recipe = resolveRecipe(level);
        recipeGrid = recipe == null ? new Ingredient[INPUT_SLOTS] : CrafterRecipeRules.gridIngredients(recipe);
        recipeGridId = selectedRecipe;
    }

    private void startCraft(ServerLevel level) {
        CraftingRecipe recipe = resolveRecipe(level);
        if (recipe == null) {
            setStatus(CrafterStatus.RECIPE_MISSING);
            retryCooldown = Config.crafterRetryIntervalTicks();
            return;
        }
        CrafterRecipePlan plan = CrafterRecipePlan.create(recipe, inputStacks(), level);
        if (plan == null) {
            setStatus(CrafterStatus.MISSING_INGREDIENTS);
            retryCooldown = Config.crafterRetryIntervalTicks();
            return;
        }
        if (!CrafterRecipePlan.canAccept(this.inventory, plan.result())) {
            setStatus(CrafterStatus.OUTPUT_FULL);
            retryCooldown = Config.crafterRetryIntervalTicks();
            return;
        }
        if (energy.getEnergyStored() < getEnergyPerTick()) {
            setStatus(CrafterStatus.NO_ENERGY);
            retryCooldown = Config.crafterRetryIntervalTicks();
            return;
        }
        setStatus(CrafterStatus.CRAFTING);
        progress = 0;
        advanceCraft(level);
    }

    private void advanceCraft(ServerLevel level) {
        int craftTime = getCraftTime();
        if (progress >= craftTime) {
            // A completed-but-blocked craft (full output) is retried on the backoff timer instead
            // of re-planning the recipe every tick. Inserting/extracting items clears the timer.
            if (retryCooldown > 0) {
                retryCooldown--;
                return;
            }
            finishCraft(level);
            return;
        }
        int cost = getEnergyPerTick();
        if (cost > 0) {
            if (energy.getEnergyStored() < cost) {
                setStatus(CrafterStatus.NO_ENERGY);
                return;
            }
            energy.consume(cost);
            workTracker.recordActiveTick(level, getMachineProgressId());
        }
        progress++;
        if (progress >= craftTime) {
            finishCraft(level);
        } else {
            setStatus(CrafterStatus.CRAFTING);
            setChanged();
        }
    }

    /**
     * Re-plans against the current inventory before consuming anything. Materials removed through
     * the GUI or a pipe while the progress bar was running therefore abort the craft without loss;
     * a full output holds the craft at the last tick until the output is emptied.
     */
    private void finishCraft(ServerLevel level) {
        CraftingRecipe recipe = resolveRecipe(level);
        if (recipe == null) {
            progress = 0;
            setStatus(CrafterStatus.RECIPE_MISSING);
            retryCooldown = Config.crafterRetryIntervalTicks();
            setChanged();
            return;
        }
        CrafterRecipePlan plan = CrafterRecipePlan.create(recipe, inputStacks(), level);
        if (plan == null) {
            progress = 0;
            setStatus(CrafterStatus.MISSING_INGREDIENTS);
            retryCooldown = Config.crafterRetryIntervalTicks();
            setChanged();
            return;
        }
        if (!CrafterRecipePlan.canAccept(this.inventory, plan.result())) {
            // Keep progress at craftTime: the ingredients remain untouched until there is room.
            setStatus(CrafterStatus.OUTPUT_FULL);
            retryCooldown = Config.crafterRetryIntervalTicks();
            setChanged();
            return;
        }
        plan.consume(this.inventory);
        CrafterRecipePlan.insertResult(this.inventory, plan.result());
        progress = 0;
        retryCooldown = 0;
        setStatus(CrafterStatus.IDLE);
        setChanged();
    }

    @Nullable
    private CraftingRecipe resolveRecipe(ServerLevel level) {
        if (selectedRecipe.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(selectedRecipe);
        if (id == null) {
            return null;
        }
        Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(id);
        if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)) {
            return null;
        }
        return CrafterRecipeRules.isSupported(recipe, level.registryAccess()) ? recipe : null;
    }

    /**
     * Changes the selected recipe. Invalid ids are ignored (the current selection stays), which
     * prevents a malformed client packet from disabling the machine.
     */
    public void setSelectedRecipe(@Nullable ResourceLocation id) {
        if (level == null || level.isClientSide) {
            return;
        }
        String next;
        if (id == null) {
            next = "";
        } else {
            Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(id);
            if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)
                    || !CrafterRecipeRules.isSupported(recipe, level.registryAccess())) {
                return;
            }
            next = id.toString();
        }
        if (next.equals(selectedRecipe)) {
            return;
        }
        selectedRecipe = next;
        progress = 0;
        retryCooldown = 0;
        status = selectedRecipe.isEmpty() ? CrafterStatus.NO_RECIPE : CrafterStatus.IDLE;
        setChanged();
        syncNow();
    }

    /**
     * Distributes one arriving stack over the input slots that belong to the selected recipe.
     *
     * <p>The stack is levelled so the per-slot counts differ by at most one, instead of filling
     * slot 0 up to 64 first, because {@link CrafterRecipePlan} requires every pattern cell to hold
     * its own ingredient. A slot that is full does not stop the others, an item that no recipe slot
     * accepts is rejected instead of being parked in a wrong slot, and nothing is ever created or
     * destroyed: the returned remainder is exactly the part that did not fit.</p>
     */
    public ItemStack insertAutomation(int requestedSlot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (level instanceof ServerLevel serverLevel) {
            // The grid cache is normally built by the machine tick, but a pipe can push items in
            // the same tick the recipe was selected.
            ensureRecipeGrid(serverLevel);
        }
        Ingredient[] grid = recipeGrid;
        if (selectedRecipe.isEmpty() || grid == null) {
            return insertAnywhere(requestedSlot, stack, simulate);
        }
        boolean matchesSomeIngredient = false;
        for (Ingredient ingredient : grid) {
            if (ingredient != null && !ingredient.isEmpty() && ingredient.test(stack)) {
                matchesSomeIngredient = true;
                break;
            }
        }
        if (!matchesSomeIngredient) {
            // Never park an item the recipe cannot use in an input slot.
            return stack;
        }

        List<CrafterDistribution.Slot> candidates = new ArrayList<>(INPUT_SLOTS);
        int[] slotIndex = new int[INPUT_SLOTS];
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            Ingredient ingredient = grid[slot];
            if (ingredient == null || ingredient.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            ItemStack current = inventory.getStackInSlot(slot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
                // Never merge different components (damage, enchantments, custom data) by sight.
                continue;
            }
            int limit = Math.min(inventory.getSlotLimit(slot), stack.getMaxStackSize());
            int count = current.isEmpty() ? 0 : current.getCount();
            int free = Math.max(0, limit - count);
            if (free <= 0) {
                continue;
            }
            slotIndex[candidates.size()] = slot;
            candidates.add(new CrafterDistribution.Slot(slot, count, free));
        }
        if (candidates.isEmpty()) {
            // The canonical slots are occupied by other items (a player may still have arranged the
            // grid differently). Fall back to any free input slot so the machine keeps working.
            return insertAnywhere(requestedSlot, stack, simulate);
        }

        int[] plan = CrafterDistribution.plan(candidates, stack.getCount());
        int remaining = stack.getCount();
        for (int index = 0; index < plan.length; index++) {
            int add = plan[index];
            if (add <= 0) {
                continue;
            }
            int slot = slotIndex[index];
            ItemStack moved = stack.copyWithCount(add);
            ItemStack rejected = inventory.insertItem(slot, moved, simulate);
            remaining -= add - rejected.getCount();
        }
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        return stack.copyWithCount(remaining);
    }

    /**
     * Inserts into the requested slot first and then any other input slot, returning the exact
     * remainder. Used when no recipe layout is available to distribute over.
     */
    private ItemStack insertAnywhere(int requestedSlot, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack;
        if (requestedSlot >= 0 && requestedSlot < INPUT_SLOTS) {
            remainder = inventory.insertItem(requestedSlot, remainder, simulate);
        }
        for (int slot = 0; slot < INPUT_SLOTS && !remainder.isEmpty(); slot++) {
            if (slot == requestedSlot) {
                continue;
            }
            remainder = inventory.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    /**
     * Phase 11.2: registers the recipe that matches the nine input slots exactly as the player
     * arranged them. The server resolves the recipe itself, so a client cannot name an arbitrary
     * result.
     *
     * @return true when a supported recipe was found and selected
     */
    public boolean registerRecipeFromGrid() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<ItemStack> stacks = inputStacks();
        boolean empty = true;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                empty = false;
                break;
            }
        }
        if (empty) {
            return false;
        }
        CraftingInput input = CraftingInput.of(3, 3, stacks);
        Optional<RecipeHolder<CraftingRecipe>> match = CrafterRecipeRules.findMatchingRecipe(
                serverLevel.getRecipeManager(), input, serverLevel);
        if (match.isEmpty()) {
            return false;
        }
        setSelectedRecipe(match.get().id());
        return match.get().id().toString().equals(selectedRecipe);
    }

    /**
     * Applies one face setting action from the GUI. Server validated: the side is clamped, the
     * action must be known and the machine is the one the player opened.
     */
    public boolean applyFaceAction(int side, int action, @Nullable ItemStack filterStack) {
        if (level == null || level.isClientSide || side < 0 || side >= 6) {
            return false;
        }
        switch (action) {
            case FACE_ACTION_MODE -> faceConfig.cycleMode(side);
            case FACE_ACTION_SLOT -> faceConfig.cycleSlot(side, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8});
            case FACE_ACTION_FILTER -> faceConfig.cycleFilter(side);
            case FACE_ACTION_ADD_FILTER_ITEM -> {
                if (filterStack == null || filterStack.isEmpty()) {
                    return false;
                }
                String id = BuiltInRegistries.ITEM.getKey(filterStack.getItem()).toString();
                faceConfig.toggleAllowed(side, id);
            }
            case FACE_ACTION_CLEAR_FILTER -> faceConfig.setAllowed(side, List.of());
            default -> {
                return false;
            }
        }
        setChanged();
        syncNow();
        return true;
    }

    private List<ItemStack> inputStacks() {
        List<ItemStack> stacks = new ArrayList<>(INPUT_SLOTS);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            stacks.add(this.inventory.getStackInSlot(slot));
        }
        return stacks;
    }

    private void setStatus(CrafterStatus next) {
        boolean working = next == CrafterStatus.CRAFTING;
        if (level instanceof ServerLevel sl) {
            BlockState st = getBlockState();
            if (st.hasProperty(com.industrialcore.block.CrafterBlock.LIT)
                    && st.getValue(com.industrialcore.block.CrafterBlock.LIT) != working) {
                sl.setBlock(worldPosition, st.setValue(com.industrialcore.block.CrafterBlock.LIT, working),
                        Block.UPDATE_CLIENTS);
            }
        }
        if (status != next) {
            status = next;
            setChanged();
        }
    }

    private void syncNow() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------
    // Menu / capabilities / drops
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.crafter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new CrafterMenu(containerId, playerInventory, this, dataAccess);
    }

    @Nullable
    @Override
    public UUID getOwnerUuid() {
        return workTracker.ownerUuid();
    }

    @Override
    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        workTracker.setOwnerUuid(ownerUuid);
    }

    @Override
    public String getMachineProgressId() {
        return "crafter";
    }

    public ModEnergyStorage getEnergyStorage() {
        return energy;
    }

    /** Nine input slots plus one output slot, with direction-safe insert/extract rules. */
    public IItemHandler getItemHandler() {
        return automationHandler;
    }

    /**
     * Capability view of one face (Phase 11.3). A null side (internal callers) sees the plain
     * automation handler, exactly as before this phase.
     */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return automationHandler;
        }
        return sideHandlers[side.get3DDataValue()];
    }

    public MachineFaceConfig getFaceConfig() {
        return faceConfig;
    }

    /** Result preview of the current input arrangement (Phase 11.2), or empty. */
    public ItemStack previewGridResult() {
        if (level == null) {
            return ItemStack.EMPTY;
        }
        CraftingInput input = CraftingInput.of(3, 3, inputStacks());
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return CrafterRecipeRules.findMatchingRecipe(level.getRecipeManager(), input, level)
                .map(holder -> holder.value().getResultItem(level.registryAccess()).copy())
                .orElse(ItemStack.EMPTY);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getContainerData() {
        return dataAccess;
    }

    public String getSelectedRecipeId() {
        return selectedRecipe;
    }

    public CrafterStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public int getCraftTime() {
        int base = CrafterSpeed.craftTime(Config.crafterCraftTimeTicks(), speedLevel);
        if (level instanceof ServerLevel sl) {
            return UpgradeMath.applyMasteryTime(base, workTracker.ownerLevel(sl, getMachineProgressId()));
        }
        return base;
    }

    public int getEnergyPerTick() {
        int raw = CrafterSpeed.energyPerTick(Config.crafterEnergyPerTick(), speedLevel);
        int base = UpgradeMath.discountedEnergy(raw, getBatteryCount());
        if (level instanceof ServerLevel sl) {
            return UpgradeMath.applyMasteryEnergy(base, workTracker.ownerLevel(sl, getMachineProgressId()));
        }
        return base;
    }

    public int getBatteryCount() {
        ItemStack stack = inventory.getStackInSlot(BATTERY_SLOT);
        return stack.is(ModItems.BATTERY.get())
                ? UpgradeMath.clampCount(stack.getCount(), UpgradeMath.MAX_UPGRADE_STACK) : 0;
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    public int getMaxSpeedLevel() {
        if (level instanceof ServerLevel sl) {
            return Math.min(CrafterSpeed.MAX_LEVEL, workTracker.upgradeLimit(sl, getMachineProgressId()));
        }
        return CrafterSpeed.MAX_LEVEL;
    }

    /**
     * Installs the motors sitting in the dedicated slot. A whole stack is consumed at once so
     * pushing in 64 motors takes effect immediately instead of over 64 ticks, and only the part
     * that still fits under the owner's unlocked machine-level cap is taken.
     */
    private boolean consumeUpgrade() {
        int cap = getMaxSpeedLevel();
        int room = cap - speedLevel;
        if (room <= 0) {
            return false;
        }
        ItemStack stack = inventory.getStackInSlot(UPGRADE_SLOT);
        if (stack.isEmpty() || !stack.is(ModItems.MOTOR.get())) {
            return false;
        }
        int install = Math.min(room, stack.getCount());
        ItemStack extracted = inventory.extractItem(UPGRADE_SLOT, install, false);
        if (extracted.isEmpty()) {
            return false;
        }
        speedLevel = CrafterSpeed.clampLevel(speedLevel + extracted.getCount());
        return true;
    }

    public int getEnergyStoredForTesting() {
        return energy.getEnergyStored();
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
            }
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        progress = 0;
        setChanged();
    }

    // ------------------------------------------------------------------
    // Persistence / sync
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_RECIPE, selectedRecipe);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_STATUS, status.ordinal());
        tag.putInt(TAG_SPEED_LEVEL, speedLevel);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        tag.put(TAG_FACES, saveFaces());
        workTracker.save(tag);
    }

    private CompoundTag saveFaces() {
        CompoundTag faces = new CompoundTag();
        for (int side = 0; side < 6; side++) {
            CompoundTag face = new CompoundTag();
            face.putByte("Mode", (byte) faceConfig.mode(side).ordinal());
            face.putInt("Slot", faceConfig.slot(side));
            face.putByte("Filter", (byte) faceConfig.filterMode(side).ordinal());
            ListTag ids = new ListTag();
            for (String id : faceConfig.allowedIds(side)) {
                ids.add(StringTag.valueOf(id));
            }
            face.put("Items", ids);
            faces.put(Integer.toString(side), face);
        }
        return faces;
    }

    private void loadFaces(CompoundTag faces) {
        for (int side = 0; side < 6; side++) {
            if (!faces.contains(Integer.toString(side))) {
                continue;
            }
            CompoundTag face = faces.getCompound(Integer.toString(side));
            faceConfig.setMode(side, FaceMode.byIndex(face.getByte("Mode") & 0xFF));
            faceConfig.setSlot(side, face.getInt("Slot"));
            faceConfig.setFilterMode(side,
                    com.industrialcore.machine.ItemFilterMode.byIndex(face.getByte("Filter") & 0xFF));
            List<String> ids = new ArrayList<>();
            ListTag items = face.getList("Items", 8);
            for (int index = 0; index < items.size(); index++) {
                ids.add(items.getString(index));
            }
            faceConfig.setAllowed(side, ids);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        selectedRecipe = tag.getString(TAG_RECIPE);
        if (tag.contains(TAG_FACES)) {
            loadFaces(tag.getCompound(TAG_FACES));
        }
        if (level != null && level.isClientSide) {
            // The client only receives the recipe id, the balance flag and the face settings.
            // Slots are synchronised by the menu and the authoritative inventory must never be
            // rebuilt from a display update.
            return;
        }
        if (tag.contains(TAG_CAPACITY)) {
            energy.setCapacity(Math.max(1, tag.getInt(TAG_CAPACITY)));
        }
        if (tag.contains(TAG_ENERGY)) {
            energy.setEnergy(tag.getInt(TAG_ENERGY));
        }
        speedLevel = CrafterSpeed.clampLevel(tag.getInt(TAG_SPEED_LEVEL));
        workTracker.load(tag);
        progress = Math.max(0, Math.min(getCraftTime(), tag.getInt(TAG_PROGRESS)));
        if (tag.contains(TAG_STATUS)) {
            CrafterStatus[] values = CrafterStatus.values();
            int ordinal = Math.max(0, Math.min(values.length - 1, tag.getInt(TAG_STATUS)));
            status = values[ordinal];
        } else {
            status = selectedRecipe.isEmpty() ? CrafterStatus.NO_RECIPE : CrafterStatus.IDLE;
        }
        if (tag.contains(TAG_INVENTORY)) {
            UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        }
        retryCooldown = 0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_RECIPE, selectedRecipe);
        tag.put(TAG_FACES, saveFaces());
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void onInventoryChanged() {
        // Wake a machine that was waiting for materials/output space.
        retryCooldown = 0;
        setChanged();
    }

    /**
     * Raw inventory with one validation rule: the output slot cannot be filled by a player or a
     * pipe. Crafting itself writes the output directly, bypassing this check.
     */
    public static final class CrafterItemHandler extends ItemStackHandler {
        private final CrafterBlockEntity owner;

        CrafterItemHandler(CrafterBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= SLOT_COUNT || slot == OUTPUT_SLOT) {
                return false;
            }
            if (slot == UPGRADE_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            return true;
        }

        @Override
        protected void onContentsChanged(int slot) {
            owner.onInventoryChanged();
        }
    }

    /**
     * Automation view: items can be inserted into the nine input slots and only extracted from the
     * output slot. This is what pipes and hoppers see, so they can never pull ingredients back out
     * or push unrelated items into the result slot.
     */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < SLOT_COUNT ? inventory.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= INPUT_SLOTS) {
                return stack;
            }
            // Goes through the recipe-aware distributor, so every automation path (pipes,
            // hoppers, the side handlers) fills the pattern slots the same way.
            return insertAutomation(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != OUTPUT_SLOT) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(OUTPUT_SLOT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < SLOT_COUNT ? inventory.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < INPUT_SLOTS;
        }
    }
}
