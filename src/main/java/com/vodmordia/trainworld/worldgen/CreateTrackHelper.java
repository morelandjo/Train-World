package com.vodmordia.trainworld.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Helper class for interfacing with Create mod track blocks.
 * Uses reflection and fallback to vanilla blocks to handle Create dependency gracefully.
 */
public class CreateTrackHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean createAvailable = false;
    private static Class<?> trackBlockClass = null;
    private static Class<?> trackShapeClass = null;
    private static Class<?> allBlocksClass = null;
    private static Class<?> scaffoldingClass = null;
    private static Object trackBlock = null;
    private static Object scaffoldingBlock = null;

    static {
        try {
            // Try to load Create classes
            trackBlockClass = Class.forName("com.simibubi.create.content.trains.track.TrackBlock");
            trackShapeClass = Class.forName("com.simibubi.create.content.trains.track.TrackShape");
            allBlocksClass = Class.forName("com.simibubi.create.AllBlocks");
            scaffoldingClass = Class.forName("com.simibubi.create.content.decoration.MetalScaffoldingBlock");

            // Get TRACK block from AllBlocks
            var trackField = allBlocksClass.getField("TRACK");
            var trackRegistryObject = trackField.get(null);
            var getMethod = trackRegistryObject.getClass().getMethod("get");
            trackBlock = getMethod.invoke(trackRegistryObject);

            // Get ANDESITE_SCAFFOLD block from AllBlocks
            var scaffoldingField = allBlocksClass.getField("ANDESITE_SCAFFOLD");
            var scaffoldingRegistryObject = scaffoldingField.get(null);
            scaffoldingBlock = getMethod.invoke(scaffoldingRegistryObject);

            createAvailable = true;
            LOGGER.info("[TrainWorld] Create mod integration successful! Track block loaded: {}", trackBlock.getClass().getName());
        } catch (Exception e) {
            createAvailable = false;
            LOGGER.warn("[TrainWorld] Create mod not available, using vanilla rails as fallback");
            LOGGER.warn("[TrainWorld] Error: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if Create mod is available.
     */
    public static boolean isCreateAvailable() {
        return createAvailable;
    }

    /**
     * Gets a track block state for a straight track in the given direction.
     */
    public static BlockState getStraightTrack(Direction direction) {
        if (!createAvailable || trackBlock == null) {
            throw new IllegalStateException("[TrainWorld] Create mod is required but not available!");
        }

        try {
            // Get the Block from trackBlock
            Block block = (Block) trackBlock;
            BlockState defaultState = block.defaultBlockState();

            // Get the SHAPE property and HAS_BE property
            var shapeProperty = trackBlockClass.getField("SHAPE").get(null);
            var hasBEProperty = trackBlockClass.getField("HAS_BE").get(null);

            // Get the appropriate TrackShape enum value
            Object shapeValue;
            if (direction == Direction.NORTH || direction == Direction.SOUTH) {
                // ZO = North-South track
                shapeValue = Enum.valueOf((Class<Enum>) trackShapeClass, "ZO");
            } else {
                // XO = East-West track
                shapeValue = Enum.valueOf((Class<Enum>) trackShapeClass, "XO");
            }

            // Set the SHAPE property
            var setValueMethod = BlockState.class.getMethod("setValue",
                net.minecraft.world.level.block.state.properties.Property.class,
                Comparable.class);
            BlockState shapedState = (BlockState) setValueMethod.invoke(defaultState, shapeProperty, shapeValue);

            // Set HAS_BE to true so the block entity is created
            // This allows trains to use the tracks and eliminates warnings
            return (BlockState) setValueMethod.invoke(shapedState, hasBEProperty, true);

        } catch (Exception e) {
            LOGGER.error("[TrainWorld] Error getting Create track block: " + e.getMessage(), e);
            throw new RuntimeException("[TrainWorld] Failed to create Create track block", e);
        }
    }

    /**
     * Gets a track block state for a diagonal track.
     */
    public static BlockState getDiagonalTrack(boolean northEastToSouthWest) {
        if (!createAvailable || trackBlock == null) {
            throw new IllegalStateException("[TrainWorld] Create mod is required but not available!");
        }

        try {
            Block block = (Block) trackBlock;
            BlockState defaultState = block.defaultBlockState();

            var shapeProperty = trackBlockClass.getField("SHAPE").get(null);
            var hasBEProperty = trackBlockClass.getField("HAS_BE").get(null);

            // PD = diagonal (NE-SW), ND = diagonal (NW-SE)
            Object shapeValue = Enum.valueOf((Class<Enum>) trackShapeClass,
                northEastToSouthWest ? "PD" : "ND");

            var setValueMethod = BlockState.class.getMethod("setValue",
                net.minecraft.world.level.block.state.properties.Property.class,
                Comparable.class);
            BlockState shapedState = (BlockState) setValueMethod.invoke(defaultState, shapeProperty, shapeValue);
            return (BlockState) setValueMethod.invoke(shapedState, hasBEProperty, true);

        } catch (Exception e) {
            LOGGER.error("[TrainWorld] Error getting diagonal track: " + e.getMessage(), e);
            throw new RuntimeException("[TrainWorld] Failed to create diagonal Create track", e);
        }
    }

    /**
     * Gets a track block state for an ascending track.
     */
    public static BlockState getAscendingTrack(Direction direction) {
        if (!createAvailable || trackBlock == null) {
            throw new IllegalStateException("[TrainWorld] Create mod is required but not available!");
        }

        try {
            Block block = (Block) trackBlock;
            BlockState defaultState = block.defaultBlockState();

            var shapeProperty = trackBlockClass.getField("SHAPE").get(null);
            var hasBEProperty = trackBlockClass.getField("HAS_BE").get(null);

            // AN, AS, AE, AW = ascending tracks
            String shapeName = switch (direction) {
                case NORTH -> "AN";
                case SOUTH -> "AS";
                case EAST -> "AE";
                case WEST -> "AW";
                default -> throw new IllegalArgumentException("Invalid direction for ascending track: " + direction);
            };

            Object shapeValue = Enum.valueOf((Class<Enum>) trackShapeClass, shapeName);

            var setValueMethod = BlockState.class.getMethod("setValue",
                net.minecraft.world.level.block.state.properties.Property.class,
                Comparable.class);
            BlockState shapedState = (BlockState) setValueMethod.invoke(defaultState, shapeProperty, shapeValue);
            return (BlockState) setValueMethod.invoke(shapedState, hasBEProperty, true);

        } catch (Exception e) {
            LOGGER.error("[TrainWorld] Error getting ascending track: " + e.getMessage(), e);
            throw new RuntimeException("[TrainWorld] Failed to create ascending Create track", e);
        }
    }

    /**
     * Gets a track block state for a crossing/junction.
     */
    public static BlockState getCrossingTrack(boolean orthogonal) {
        if (!createAvailable || trackBlock == null) {
            throw new IllegalStateException("[TrainWorld] Create mod is required but not available!");
        }

        try {
            Block block = (Block) trackBlock;
            BlockState defaultState = block.defaultBlockState();

            var shapeProperty = trackBlockClass.getField("SHAPE").get(null);
            var hasBEProperty = trackBlockClass.getField("HAS_BE").get(null);

            // CR_O = orthogonal crossing (N-S + E-W)
            // CR_D = diagonal crossing
            Object shapeValue = Enum.valueOf((Class<Enum>) trackShapeClass,
                orthogonal ? "CR_O" : "CR_D");

            var setValueMethod = BlockState.class.getMethod("setValue",
                net.minecraft.world.level.block.state.properties.Property.class,
                Comparable.class);
            BlockState shapedState = (BlockState) setValueMethod.invoke(defaultState, shapeProperty, shapeValue);
            return (BlockState) setValueMethod.invoke(shapedState, hasBEProperty, true);

        } catch (Exception e) {
            LOGGER.error("[TrainWorld] Error getting crossing track: " + e.getMessage(), e);
            throw new RuntimeException("[TrainWorld] Failed to create crossing Create track", e);
        }
    }

    /**
     * Gets the metal scaffolding block from Create.
     */
    public static BlockState getScaffoldingBlock() {
        if (!createAvailable || scaffoldingBlock == null) {
            throw new IllegalStateException("[TrainWorld] Create mod is required but not available!");
        }

        try {
            Block block = (Block) scaffoldingBlock;
            return block.defaultBlockState();
        } catch (Exception e) {
            LOGGER.error("[TrainWorld] Error getting scaffolding block: " + e.getMessage(), e);
            throw new RuntimeException("[TrainWorld] Failed to get Create scaffolding block", e);
        }
    }

    /**
     * Gets stone bricks for bridge decking and supports.
     */
    public static BlockState getBridgeDeckBlock() {
        // Use stone bricks for bridge deck and supports
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    /**
     * Gets stone bricks for tunnel walls.
     */
    public static BlockState getTunnelWallBlock() {
        return Blocks.STONE_BRICKS.defaultBlockState();
    }
}
