package com.industrialcore.block;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model-accurate collision, selection and outline shapes (Phase 79).
 *
 * <p>The boxes live in {@link ModelShapeData}, measured from the shipped block models by
 * {@code tools/gen_p79_shapes_and_items.py}. This class only converts them to
 * {@link VoxelShape}s, applies the horizontal facing with the same quarter turn the blockstate
 * uses for the model, and caches the result per state (block states are singletons).</p>
 *
 * <p>Conduits are not in the tables: their boxes depend on the six connection booleans and are
 * built by {@link CableBlock} and {@link ItemPipeBlock} from {@link TubeShapes}, which is why
 * {@link ModelShapeData#isDynamic(String)} reports them separately.</p>
 */
public final class ModelShapeRegistry {
    private static final Map<BlockState, VoxelShape> CACHE = new IdentityHashMap<>();

    private ModelShapeRegistry() {
    }

    /**
     * The shape of one state.
     *
     * <p>The tables are stored in model (north) orientation. A variant that rotates with the
     * horizontal facing is turned exactly like the blockstate turns the model, so the outline
     * always sits on the drawn geometry. Blocks without a table keep the plain cube.</p>
     */
    public static VoxelShape shape(BlockState state) {
        VoxelShape cached = CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        List<ModelShapeData.Variant> variants = ModelShapeData.variants(path);
        ModelShapeData.Variant variant = find(variants, keyOf(state, true));
        Direction facing = horizontalFacing(state);
        if (variant == null) {
            // Variants whose models differ per facing are stored with the facing in the key.
            variant = find(variants, keyOf(state, false));
            facing = null;
        }
        VoxelShape shape = variant == null ? Shapes.block() : build(variant, facing);
        CACHE.put(state, shape);
        return shape;
    }

    /** Quarter turn of a pixel box about the block centre, identical to the blockstate rotation. */
    public static double[] rotate(double[] box, Direction facing) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        return ModelShapeData.turn(box, turns);
    }

    private static ModelShapeData.Variant find(List<ModelShapeData.Variant> variants, String key) {
        for (ModelShapeData.Variant variant : variants) {
            if (variant.key().equals(key)) {
                return variant;
            }
        }
        return null;
    }

    /**
     * State key in the same normalised form the generator writes: sorted {@code name=value}
     * pairs. The facing is dropped when the table rotates with it.
     */
    private static String keyOf(BlockState state, boolean dropFacing) {
        List<String> parts = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            if (dropFacing && property == BlockStateProperties.HORIZONTAL_FACING) {
                continue;
            }
            parts.add(property.getName() + "=" + state.getValue(property));
        }
        Collections.sort(parts);
        return String.join(",", parts);
    }

    private static Direction horizontalFacing(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
    }

    private static VoxelShape build(ModelShapeData.Variant variant, Direction facing) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : variant.boxes()) {
            double[] turned = facing != null && variant.rotatesWithFacing()
                    ? rotate(box, facing) : box;
            shape = Shapes.or(shape, Block.box(turned[0], turned[1], turned[2],
                    turned[3], turned[4], turned[5]));
        }
        return shape.isEmpty() ? Shapes.block() : shape.optimize();
    }
}
