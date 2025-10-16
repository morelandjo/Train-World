package com.vodmordia.trainworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Handles bridge and scaffolding generation for elevated tracks.
 */
public class TrackBridgeBuilder {

    private final TrainWorldConfig config;

    public TrackBridgeBuilder(TrainWorldConfig config) {
        this.config = config;
    }

    /**
     * Generates a scaffolding pillar from trackHeight down to the ground.
     */
    public void generateScaffoldingPillar(WorldGenLevel level, BlockPos pillarTop) {
        if (!config.TRACK_USE_SCAFFOLDING_SUPPORTS) {
            return;
        }

        // Get ground height
        int groundHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pillarTop.getX(), pillarTop.getZ());

        // Get scaffolding block from Create (we'll use a placeholder for now and fix it later)
        BlockState scaffolding = getScaffoldingBlock();

        // Build pillar from ground to track height
        for (int y = groundHeight; y < pillarTop.getY(); y++) {
            BlockPos pos = new BlockPos(pillarTop.getX(), y, pillarTop.getZ());

            // Only place if air or replaceable
            if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, scaffolding, 3);
            }
        }
    }

    /**
     * Generates scaffolding supports along a track section.
     */
    public void generateScaffoldingAlongTrack(WorldGenLevel level, BlockPos start, BlockPos end, int trackHeight) {
        if (!config.TRACK_USE_SCAFFOLDING_SUPPORTS) {
            return;
        }

        // Calculate positions for scaffolding pillars
        BlockPos[] pillarPositions = calculatePillarPositions(start, end);

        // Generate each pillar
        for (BlockPos pillarPos : pillarPositions) {
            BlockPos pillarTop = new BlockPos(pillarPos.getX(), trackHeight, pillarPos.getZ());
            generateScaffoldingPillar(level, pillarTop);
        }
    }

    /**
     * Calculates evenly spaced pillar positions between start and end.
     */
    private BlockPos[] calculatePillarPositions(BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Calculate number of pillars needed
        int numPillars = Math.max(1, (int) Math.ceil(distance / config.SCAFFOLDING_SPACING));

        BlockPos[] pillars = new BlockPos[numPillars];
        for (int i = 0; i < numPillars; i++) {
            double t = (i + 1.0) / (numPillars + 1.0);
            int x = (int) (start.getX() + dx * t);
            int z = (int) (start.getZ() + dz * t);
            pillars[i] = new BlockPos(x, start.getY(), z);
        }

        return pillars;
    }

    /**
     * Generates a bridge deck (flat surface for tracks).
     */
    public void generateBridgeDeck(WorldGenLevel level, BlockPos start, BlockPos end, int trackHeight) {
        // Get bridge deck material
        BlockState deckMaterial = getBridgeDeckBlock();

        // Calculate direction
        int dx = Integer.compare(end.getX(), start.getX());
        int dz = Integer.compare(end.getZ(), start.getZ());

        BlockPos current = start;
        while (!current.equals(end)) {
            // Place deck block below track position
            BlockPos deckPos = current.below();

            if (level.getBlockState(deckPos).isAir() || level.getBlockState(deckPos).canBeReplaced()) {
                level.setBlock(deckPos, deckMaterial, 3);
            }

            // Also place side supports (2 blocks wide)
            if (dx != 0) { // East-West bridge
                placeSupportBlock(level, deckPos.north(), deckMaterial);
                placeSupportBlock(level, deckPos.south(), deckMaterial);
            } else if (dz != 0) { // North-South bridge
                placeSupportBlock(level, deckPos.east(), deckMaterial);
                placeSupportBlock(level, deckPos.west(), deckMaterial);
            }

            // Move to next position
            current = current.offset(dx, 0, dz);
        }
    }

    /**
     * Places a support block if the position is valid.
     */
    private void placeSupportBlock(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, state, 3);
        }
    }

    /**
     * Checks if a bridge is needed at this location.
     */
    public boolean needsBridge(WorldGenLevel level, BlockPos pos, Direction direction, int trackHeight) {
        if (!config.TRACK_USE_BRIDGES) {
            return false;
        }

        // Check ground height
        int groundHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());

        // If track is significantly above ground, we need a bridge
        int heightDifference = trackHeight - groundHeight;

        return heightDifference > config.TRACK_BRIDGE_HEIGHT_THRESHOLD;
    }

    /**
     * Checks if the track crosses water.
     */
    public boolean crossesWater(WorldGenLevel level, BlockPos start, BlockPos end) {
        // Sample points between start and end
        int samples = 8;
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();

        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            int x = (int) (start.getX() + dx * t);
            int z = (int) (start.getZ() + dz * t);

            // Check surface height
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            int oceanFloorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);

            // If there's a difference, there's water
            if (surfaceY != oceanFloorY) {
                return true;
            }
        }

        return false;
    }

    /**
     * Generates a complete bridge structure.
     */
    public void generateBridge(WorldGenLevel level, BlockPos start, BlockPos end, int trackHeight, Direction direction) {
        // Generate scaffolding pillars
        generateScaffoldingAlongTrack(level, start, end, trackHeight);

        // Generate bridge deck
        generateBridgeDeck(level, start, end, trackHeight);
    }

    /**
     * Gets the scaffolding block from Create mod.
     */
    private BlockState getScaffoldingBlock() {
        return CreateTrackHelper.getScaffoldingBlock();
    }

    /**
     * Gets the bridge deck block material.
     */
    private BlockState getBridgeDeckBlock() {
        return CreateTrackHelper.getBridgeDeckBlock();
    }

    /**
     * Calculates the optimal bridge height based on terrain.
     */
    public int calculateBridgeHeight(WorldGenLevel level, BlockPos start, BlockPos end) {
        int maxGroundHeight = Integer.MIN_VALUE;

        // Sample ground heights between start and end
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int samples = Math.max(Math.abs(dx), Math.abs(dz)) / 4; // Sample every 4 blocks

        for (int i = 0; i <= samples; i++) {
            double t = i / (double) Math.max(1, samples);
            int x = (int) (start.getX() + dx * t);
            int z = (int) (start.getZ() + dz * t);

            int groundHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            maxGroundHeight = Math.max(maxGroundHeight, groundHeight);
        }

        // Bridge should be at least BRIDGE_HEIGHT_THRESHOLD above the highest point
        return maxGroundHeight + config.TRACK_BRIDGE_HEIGHT_THRESHOLD;
    }
}
