package com.industrialcore.compat.jei;

import com.industrialcore.IndustrialCore;
import com.industrialcore.client.JeiSearchBridge;
import com.industrialcore.client.QuantumStorageScreen;
import com.industrialcore.machine.AlloyRecipe;
import com.industrialcore.machine.AmplificationRecipe;
import com.industrialcore.machine.AssemblerRecipe;
import com.industrialcore.machine.CrushingRecipe;
import com.industrialcore.machine.CultivationRecipe;
import com.industrialcore.machine.PhaseReactionRecipe;
import com.industrialcore.machine.ProcessorRecipe;
import com.industrialcore.machine.ReconstructionRecipe;
import com.industrialcore.machine.SynthesisRecipe;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.storage.QuantumStorageLayout;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * JEI integration of the machine recipes.
 *
 * <p>This is the entry point JEI finds through the {@code @JeiPlugin} annotation. It walks
 * {@link JeiCoverage}, which lists every recipe type of the mod together with its machine, and
 * registers a category, the recipes of that type and the machines that craft them. Nothing is
 * duplicated: the categories read the live recipe objects, so an edited or data-pack-added recipe
 * is what the player sees.</p>
 *
 * <p>JEI is an optional client mod. Every class of this package is only loaded by JEI itself, so
 * a dedicated server, a GameTest run and a client without JEI never touch these classes; the
 * machines keep working exactly as before.</p>
 */
@JeiPlugin
public final class IndustrialCoreJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "jei_plugin");

    public static final RecipeType<CrushingRecipe> CRUSHING =
            RecipeType.create(IndustrialCore.MOD_ID, "crushing", CrushingRecipe.class);
    public static final RecipeType<ProcessorRecipe> ELECTROLYSIS =
            RecipeType.create(IndustrialCore.MOD_ID, "electrolysis", ProcessorRecipe.class);
    public static final RecipeType<ProcessorRecipe> PRECISION_CRUSHING =
            RecipeType.create(IndustrialCore.MOD_ID, "precision_crushing", ProcessorRecipe.class);
    public static final RecipeType<ProcessorRecipe> PRESSING =
            RecipeType.create(IndustrialCore.MOD_ID, "pressing", ProcessorRecipe.class);
    public static final RecipeType<AlloyRecipe> ALLOYING =
            RecipeType.create(IndustrialCore.MOD_ID, "alloying", AlloyRecipe.class);
    public static final RecipeType<ReconstructionRecipe> RECONSTRUCTION =
            RecipeType.create(IndustrialCore.MOD_ID, "reconstruction", ReconstructionRecipe.class);
    public static final RecipeType<AssemblerRecipe> ASSEMBLING =
            RecipeType.create(IndustrialCore.MOD_ID, "assembling", AssemblerRecipe.class);
    public static final RecipeType<CultivationRecipe> CULTIVATION =
            RecipeType.create(IndustrialCore.MOD_ID, "cultivation", CultivationRecipe.class);
    public static final RecipeType<AmplificationRecipe> AMPLIFICATION =
            RecipeType.create(IndustrialCore.MOD_ID, "amplification", AmplificationRecipe.class);
    public static final RecipeType<PhaseReactionRecipe> PHASE_REACTION =
            RecipeType.create(IndustrialCore.MOD_ID, "phase_reaction", PhaseReactionRecipe.class);
    public static final RecipeType<SynthesisRecipe> SYNTHESIS =
            RecipeType.create(IndustrialCore.MOD_ID, "synthesis", SynthesisRecipe.class);
    public static final RecipeType<PowerGenerationCatalog.Entry> POWER_GENERATION =
            RecipeType.create(IndustrialCore.MOD_ID, "power_generation", PowerGenerationCatalog.Entry.class);

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (JeiCoverage.Category spec : JeiCoverage.categories()) {
            Definition<?> definition = definition(spec.recipeTypeId());
            if (definition == null) {
                LOGGER.error("No JEI category is defined for the recipe type {}", spec.jeiUid());
                continue;
            }
            addCategory(registration, gui, spec, definition);
        }
        registration.addRecipeCategories(new PowerGenerationJeiCategory(gui, POWER_GENERATION));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        RecipeManager manager = recipeManager();
        if (manager == null) {
            LOGGER.warn("JEI asked for the recipes before a world was loaded; no machine recipes "
                    + "are shown this time");
            return;
        }
        int categories = 0;
        int recipes = 0;
        for (JeiCoverage.Category spec : JeiCoverage.categories()) {
            Definition<?> definition = definition(spec.recipeTypeId());
            if (definition == null) {
                continue;
            }
            categories++;
            recipes += addRecipes(registration, manager, spec, definition);
        }
        registration.addRecipes(POWER_GENERATION, PowerGenerationCatalog.entries());
        LOGGER.info("JEI: {} machine recipe categories with {} recipes + {} power generation entries",
                categories, recipes, PowerGenerationCatalog.entries().size());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (JeiCoverage.Category spec : JeiCoverage.categories()) {
            Definition<?> definition = definition(spec.recipeTypeId());
            if (definition == null) {
                continue;
            }
            for (String id : spec.catalysts()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                if (item == Items.AIR) {
                    LOGGER.warn("The JEI catalyst {} of {} is not a registered item", id,
                            spec.jeiUid());
                    continue;
                }
                registration.addRecipeCatalyst(new ItemStack(item), definition.jeiType());
            }
        }
        // The electric furnace runs the vanilla smelting recipes, which JEI already shows; this
        // only adds the machine to them, it does not add a second copy of any recipe.
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.ELECTRIC_FURNACE.get()),
                RecipeTypes.SMELTING);
        // Quantum Storage Core features a built-in 3x3 auto-refill crafting bay.
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.QUANTUM_STORAGE_CORE.get()),
                RecipeTypes.CRAFTING);
        // Phase 105.5: Register every IndustrialCore power generator as a Power Generation catalyst
        for (String genId : PowerGenerationCatalog.catalysts()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(genId));
            if (item != Items.AIR) {
                registration.addRecipeCatalyst(new ItemStack(item), POWER_GENERATION);
            }
        }
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new QuantumStorageJeiTransferHandler(registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(
                QuantumStorageScreen.class,
                new QuantumStorageJeiGuiHandler());
        registration.addGuiContainerHandler(
                com.industrialcore.client.SingularityReactorScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
                    @Override
                    public List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(
                            com.industrialcore.client.SingularityReactorScreen screen) {
                        var card = com.industrialcore.endgame.SingularityReactorLayout.RIFT_CARD;
                        float scale = screen.panelScale();
                        int sx = screen.panelX() + Math.round(card.x() * scale);
                        int sy = screen.panelY() + Math.round(card.y() * scale);
                        int sw = Math.max(1, Math.round(card.width() * scale) + 2);
                        int sh = Math.max(1, Math.round(card.height() * scale) + 2);
                        return List.of(new net.minecraft.client.renderer.Rect2i(sx, sy, sw, sh));
                    }
                });
        registration.addRecipeClickArea(
                QuantumStorageScreen.class,
                QuantumStorageLayout.CRAFT_ARROW.x(),
                QuantumStorageLayout.CRAFT_ARROW.y(),
                QuantumStorageLayout.CRAFT_ARROW.width(),
                QuantumStorageLayout.CRAFT_ARROW.height(),
                RecipeTypes.CRAFTING);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        IIngredientFilter filter = jeiRuntime.getIngredientFilter();
        JeiSearchBridge.bind(filter::getFilterText, filter::setFilterText);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiSearchBridge.unbind();
    }

    private static <T extends Recipe<?>> void addCategory(IRecipeCategoryRegistration registration,
                                                          IGuiHelper gui,
                                                          JeiCoverage.Category spec,
                                                          Definition<T> definition) {
        registration.addRecipeCategories(
                new JeiCategory<>(gui, spec, definition.jeiType(), definition.view()));
    }

    /**
     * Registers the recipes of one type.
     *
     * <p>The recipe type is looked up in the registry by the id of the coverage entry, so the
     * table cannot describe a machine the mod does not register.</p>
     */
    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> int addRecipes(IRecipeRegistration registration,
                                                        RecipeManager manager,
                                                        JeiCoverage.Category spec,
                                                        Definition<T> definition) {
        var minecraftType = (net.minecraft.world.item.crafting.RecipeType<T>)
                BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse(spec.jeiUid()));
        if (minecraftType == null) {
            LOGGER.error("There is no recipe type {} to show in JEI", spec.jeiUid());
            return 0;
        }
        List<T> recipes = recipesOf(manager, minecraftType);
        if (definition.order() != null) {
            recipes.sort(definition.order());
        }
        registration.addRecipes(definition.jeiType(), recipes);
        return recipes.size();
    }

    /**
     * The recipes of one type.
     *
     * <p>{@code RecipeManager#getAllRecipesFor} is typed with the input type of the recipe, which
     * a table-driven lookup cannot name; the type is still the one the mod registered, so the
     * values are of the expected class.</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Recipe<?>> List<T> recipesOf(RecipeManager manager,
                                                           net.minecraft.world.item.crafting.RecipeType<T> type) {
        List<T> recipes = new ArrayList<>();
        List<RecipeHolder> holders = manager.getAllRecipesFor((net.minecraft.world.item.crafting.RecipeType) type);
        for (RecipeHolder holder : holders) {
            recipes.add((T) holder.value());
        }
        return recipes;
    }

    private static RecipeManager recipeManager() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getRecipeManager();
    }

    /**
     * The JEI side of one recipe type: its category type, the adapter that reads the recipe, and
     * an optional display order.
     *
     * <p>A missing case is a bug, not a runtime condition: the tests compare these ids with
     * {@link JeiCoverage} and with the registered recipe types.</p>
     */
    private static Definition<?> definition(String recipeTypeId) {
        return switch (recipeTypeId) {
            case "crushing" -> Definition.of(CRUSHING, MachineRecipeViews.CRUSHING);
            case "electrolysis" -> Definition.of(ELECTROLYSIS, MachineRecipeViews.PROCESSOR);
            case "precision_crushing" ->
                    Definition.of(PRECISION_CRUSHING, MachineRecipeViews.PROCESSOR);
            case "pressing" -> Definition.of(PRESSING, MachineRecipeViews.PROCESSOR);
            case "alloying" -> Definition.of(ALLOYING, MachineRecipeViews.ALLOYING);
            case "reconstruction" ->
                    Definition.of(RECONSTRUCTION, MachineRecipeViews.RECONSTRUCTION);
            case "assembling" -> Definition.of(ASSEMBLING, MachineRecipeViews.ASSEMBLING);
            case "cultivation" -> Definition.of(CULTIVATION, MachineRecipeViews.CULTIVATION);
            case "amplification" -> Definition.of(AMPLIFICATION, MachineRecipeViews.AMPLIFICATION);
            case "phase_reaction" ->
                    Definition.of(PHASE_REACTION, MachineRecipeViews.PHASE_REACTION);
            case "synthesis" -> Definition.of(SYNTHESIS, MachineRecipeViews.SYNTHESIS);
            default -> null;
        };
    }

    private record Definition<T extends Recipe<?>>(RecipeType<T> jeiType,
                                                   MachineRecipeViews.View<T> view,
                                                   Comparator<T> order) {
        static <T extends Recipe<?>> Definition<T> of(RecipeType<T> jeiType,
                                                      MachineRecipeViews.View<T> view) {
            return new Definition<>(jeiType, view, null);
        }
    }
}
