package com.vodmordia.trainworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages terrain height information and integration for track placement.
 * Handles bridges, tunnels, and scaffolding generation.
 */
public class TrainWorldHeightMapper {

    private final TrainWorldConfig config;
    private final Map<ChunkPos, ChunkHeightInfo> heightCache;

    public TrainWorldHeightMapper(TrainWorldConfig config) {
        this.config = config;
        this.heightCache = Collections.synchronizedMap(new HashMap<>());
    }

    /**
     * Information about terrain heights in a chunk.
     */
    public static class ChunkHeightInfo {
        private final int minHeight;
        private final int maxHeight;
        private final int averageHeight;
        private final int[] heightmap; // 16x16 grid of heights

        public ChunkHeightInfo(int minHeight, int maxHeight, int averageHeight, int[] heightmap) {
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.averageHeight = averageHeight;
            this.heightmap = heightmap;
        }

        public int getMinHeight() {
            return minHeight;
        }

        public int getMaxHeight() {
            return maxHeight;
        }

        public int getAverageHeight() {
            return averageHeight;
        }

        public int getHeightAt(int localX, int localZ) {
            if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
                return averageHeight;
            }
            return heightmap[localZ * 16 + localX];
        }

        public int getHeightVariation() {
            return maxHeight - minHeight;
        }

        public boolean isFlat() {
            return getHeightVariation() < 5;
        }

        public boolean isRugged() {
            return getHeightVariation() > 15;
        }
    }

    /**
     * Gets height information for a chunk.
     */
    public ChunkHeightInfo getChunkHeightInfo(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        return heightCache.computeIfAbsent(pos, p -> calculateChunkHeightInfo(chunk));
    }

    /**
     * Calculates height information for a chunk.
     */
    private ChunkHeightInfo calculateChunkHeightInfo(ChunkAccess chunk) {
        int[] heightmap = new int[256]; // 16x16
        int sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);

                // Check for water and adjust height to be at least 2 blocks above water level
                BlockPos checkPos = chunk.getPos().getWorldPosition().offset(x, height, z);
                if (isWaterAt(chunk, checkPos)) {
                    // Find the water surface
                    int waterSurface = height;
                    for (int y = height; y < chunk.getMaxBuildHeight(); y++) {
                        if (!isWaterAt(chunk, checkPos.atY(y))) {
                            waterSurface = y;
                            break;
                        }
                    }
                    // Set height to be 2 blocks above water surface
                    height = waterSurface + 2;
                }

                heightmap[z * 16 + x] = height;
                sum += height;
                min = Math.min(min, height);
                max = Math.max(max, height);
            }
        }

        int average = sum / 256;
        return new ChunkHeightInfo(min, max, average, heightmap);
    }

    /**
     * Checks if there is water at the given position.
     */
    private boolean isWaterAt(ChunkAccess chunk, BlockPos pos) {
        try {
            BlockState state = chunk.getBlockState(pos);
            return state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determines the optimal track height for a chunk based on terrain.
     */
    public int calculateOptimalTrackHeight(ChunkAccess chunk, TrackNetworkChunk trackInfo) {
        ChunkHeightInfo heightInfo = getChunkHeightInfo(chunk);

        // For tunnels, place below surface
        if (trackInfo.getType().isTunnel()) {
            return heightInfo.getAverageHeight() - config.TRACK_TUNNEL_DEPTH;
        }

        // For bridges, maintain consistent height
        if (trackInfo.getType().isBridge()) {
            return heightInfo.getAverageHeight() + config.TRACK_BRIDGE_HEIGHT_THRESHOLD;
        }

        // For regular tracks, follow terrain
        if (config.FOLLOW_TERRAIN_CLOSELY) {
            return heightInfo.getAverageHeight() + config.TRACK_SURFACE_HEIGHT_OFFSET;
        }

        // Use configured height range
        int targetHeight = (config.MIN_TERRAIN_HEIGHT + config.MAX_TERRAIN_HEIGHT) / 2;
        return Math.max(config.MIN_TERRAIN_HEIGHT,
                Math.min(config.MAX_TERRAIN_HEIGHT, targetHeight));
    }

    /**
     * Determines if a bridge is needed between two chunks.
     */
    public boolean needsBridge(ChunkAccess currentChunk, ChunkAccess neighborChunk, TrackNetworkChunk.TrackDirection direction) {
        if (!config.TRACK_USE_BRIDGES) {
            return false;
        }

        ChunkHeightInfo current = getChunkHeightInfo(currentChunk);
        ChunkHeightInfo neighbor = getChunkHeightInfo(neighborChunk);

        // Check height difference
        int heightDiff = Math.abs(current.getAverageHeight() - neighbor.getAverageHeight());
        if (heightDiff > config.TRACK_BRIDGE_HEIGHT_THRESHOLD) {
            return true;
        }

        // Check for gaps (large height drops between the chunks)
        return hasSignificantGap(currentChunk, neighborChunk, direction);
    }

    /**
     * Checks if there's a significant gap between chunks.
     */
    private boolean hasSignificantGap(ChunkAccess currentChunk, ChunkAccess neighborChunk, TrackNetworkChunk.TrackDirection direction) {
        ChunkHeightInfo current = getChunkHeightInfo(currentChunk);
        ChunkHeightInfo neighbor = getChunkHeightInfo(neighborChunk);

        // Sample edge heights based on direction
        int[] edgeHeights = getEdgeHeights(current, direction);
        int minEdgeHeight = Integer.MAX_VALUE;
        for (int h : edgeHeights) {
            minEdgeHeight = Math.min(minEdgeHeight, h);
        }

        // If the minimum edge height is significantly lower than average, we need a bridge
        int heightDrop = current.getAverageHeight() - minEdgeHeight;
        return heightDrop > config.TRACK_BRIDGE_HEIGHT_THRESHOLD;
    }

    /**
     * Gets heights along the edge of a chunk in a given direction.
     */
    private int[] getEdgeHeights(ChunkHeightInfo info, TrackNetworkChunk.TrackDirection direction) {
        int[] heights = new int[16];

        if (direction.allowsNorth()) {
            for (int x = 0; x < 16; x++) {
                heights[x] = info.getHeightAt(x, 0);
            }
        } else if (direction.allowsSouth()) {
            for (int x = 0; x < 16; x++) {
                heights[x] = info.getHeightAt(x, 15);
            }
        } else if (direction.allowsEast()) {
            for (int z = 0; z < 16; z++) {
                heights[z] = info.getHeightAt(15, z);
            }
        } else if (direction.allowsWest()) {
            for (int z = 0; z < 16; z++) {
                heights[z] = info.getHeightAt(0, z);
            }
        } else {
            // Default: return average heights
            for (int i = 0; i < 16; i++) {
                heights[i] = info.getAverageHeight();
            }
        }

        return heights;
    }

    /**
     * Determines if a tunnel is needed based on terrain.
     */
    public boolean needsTunnel(ChunkAccess chunk, TrackNetworkChunk trackInfo) {
        ChunkHeightInfo heightInfo = getChunkHeightInfo(chunk);

        // If terrain is very rugged, use tunnel
        if (heightInfo.isRugged()) {
            return true;
        }

        // If we're in mountains (high terrain), use tunnel
        if (heightInfo.getAverageHeight() > config.MAX_TERRAIN_HEIGHT) {
            return true;
        }

        // Check slope - if too steep, tunnel through
        float slope = calculateTerrainSlope(chunk, trackInfo.getDirection());
        return slope > config.MAX_SURFACE_SLOPE;
    }

    /**
     * Calculates the slope of terrain in a given direction.
     */
    private float calculateTerrainSlope(ChunkAccess chunk, TrackNetworkChunk.TrackDirection direction) {
        ChunkHeightInfo info = getChunkHeightInfo(chunk);

        if (direction.allowsNorth() || direction.allowsSouth()) {
            // North-South slope
            int northHeight = 0;
            int southHeight = 0;
            for (int x = 0; x < 16; x++) {
                northHeight += info.getHeightAt(x, 0);
                southHeight += info.getHeightAt(x, 15);
            }
            northHeight /= 16;
            southHeight /= 16;
            return Math.abs(southHeight - northHeight) / 16.0f;
        } else if (direction.allowsEast() || direction.allowsWest()) {
            // East-West slope
            int eastHeight = 0;
            int westHeight = 0;
            for (int z = 0; z < 16; z++) {
                eastHeight += info.getHeightAt(15, z);
                westHeight += info.getHeightAt(0, z);
            }
            eastHeight /= 16;
            westHeight /= 16;
            return Math.abs(eastHeight - westHeight) / 16.0f;
        }

        return 0.0f;
    }

    /**
     * Calculates scaffolding positions for a bridge or elevated track.
     */
    public BlockPos[] calculateScaffoldingPositions(BlockPos start, BlockPos end, int trackHeight) {
        if (!config.TRACK_USE_SCAFFOLDING_SUPPORTS) {
            return new BlockPos[0];
        }

        // Calculate distance
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Calculate number of pillars needed
        int numPillars = (int) Math.ceil(distance / config.SCAFFOLDING_SPACING);

        BlockPos[] pillars = new BlockPos[numPillars];
        for (int i = 0; i < numPillars; i++) {
            double t = (i + 1.0) / (numPillars + 1.0);
            int x = (int) (start.getX() + dx * t);
            int z = (int) (start.getZ() + dz * t);
            pillars[i] = new BlockPos(x, trackHeight, z);
        }

        return pillars;
    }

    /**
     * Smooths height transitions between chunks.
     */
    public int calculateSmoothedHeight(int currentHeight, int neighborHeight) {
        float smoothing = config.HEIGHT_SMOOTHING_FACTOR;
        return (int) (currentHeight * (1.0f - smoothing) + neighborHeight * smoothing);
    }

    /**
     * Clears the height cache.
     */
    public void clearCache() {
        heightCache.clear();
    }
}
