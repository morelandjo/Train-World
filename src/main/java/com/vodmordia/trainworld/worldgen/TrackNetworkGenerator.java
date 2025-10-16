package com.vodmordia.trainworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates deterministic track network layout across chunks.
 * Uses grid-based routing with Perlin noise for organic variation.
 * Inspired by LostCities' Railway system.
 */
public class TrackNetworkGenerator {

    private final TrainWorldConfig config;
    private final long seed;
    private final Map<ChunkPos, TrackNetworkChunk> cache;
    private final ThreadLocal<Boolean> isCalculatingConnections = ThreadLocal.withInitial(() -> false);

    public TrackNetworkGenerator(TrainWorldConfig config, long seed) {
        this.config = config;
        this.seed = seed + config.TRACK_SEED_OFFSET;
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Gets track network info for a chunk, using cache if available.
     */
    public TrackNetworkChunk getTrackChunk(ChunkPos chunkPos) {
        if (!config.TRACKS_ENABLED) {
            return TrackNetworkChunk.EMPTY;
        }

        // Check cache first
        TrackNetworkChunk cached = cache.get(chunkPos);
        if (cached != null) {
            return cached;
        }

        // Calculate and cache the result
        TrackNetworkChunk result = calculateTrackChunk(chunkPos);
        cache.put(chunkPos, result);
        return result;
    }

    /**
     * Clears the cache. Call this when configuration changes or world reloads.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Calculates track network info for a chunk using deterministic rules.
     */
    private TrackNetworkChunk calculateTrackChunk(ChunkPos chunkPos) {
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;

        // Create deterministic random for this chunk
        RandomSource random = createChunkRandom(chunkX, chunkZ);

        // Check Perlin noise for track density
        if (!shouldGenerateTrackAtLocation(chunkX, chunkZ, random)) {
            return TrackNetworkChunk.EMPTY;
        }

        // Calculate grid position (offset by 1 to avoid alignment with potential highway systems)
        int gridX = Math.floorMod(chunkX + 1, config.STATION_SPACING);
        int gridZ = Math.floorMod(chunkZ + 1, config.STATION_SPACING);

        // Determine if this is a station location
        if (isStationLocation(gridX, gridZ)) {
            if (random.nextFloat() < config.STATION_GENERATION_CHANCE) {
                return createStationChunk(chunkPos, random);
            }
        }

        // Check if this is a major route location (grid-aligned)
        TrackNetworkChunk majorRoute = checkMajorRoute(chunkPos, gridX, gridZ, random);
        if (majorRoute != null && !majorRoute.isEmpty()) {
            return majorRoute;
        }

        // Check for connecting tracks between major routes
        // Skip this check if we're already calculating connections to prevent infinite recursion
        if (!isCalculatingConnections.get()) {
            TrackNetworkChunk connecting = calculateConnectingTrack(chunkPos, random);
            if (connecting != null && !connecting.isEmpty()) {
                return connecting;
            }
        }

        return TrackNetworkChunk.EMPTY;
    }

    /**
     * Creates a deterministic random source for a chunk.
     */
    private RandomSource createChunkRandom(int chunkX, int chunkZ) {
        long chunkSeed = seed;
        chunkSeed = chunkSeed * 31 + chunkX;
        chunkSeed = chunkSeed * 31 + chunkZ;
        return RandomSource.create(chunkSeed);
    }

    /**
     * Checks if tracks should generate at this location using Perlin noise.
     */
    private boolean shouldGenerateTrackAtLocation(int chunkX, int chunkZ, RandomSource random) {
        // Always generate at station locations
        int gridX = Math.floorMod(chunkX + 1, config.STATION_SPACING);
        int gridZ = Math.floorMod(chunkZ + 1, config.STATION_SPACING);
        if (isStationLocation(gridX, gridZ)) {
            return true;
        }

        // Always generate at major route locations (grid-aligned tracks)
        int halfSpacing = config.STATION_SPACING / 2;
        if (gridX == 0 || gridX == halfSpacing || gridZ == 0 || gridZ == halfSpacing) {
            return true; // Major route - always generate
        }

        // Use Perlin noise for organic distribution
        double noiseX = chunkX / (double) config.TRACK_PERLIN_SCALE;
        double noiseZ = chunkZ / (double) config.TRACK_PERLIN_SCALE;

        // Simple noise approximation (in real implementation, use proper PerlinNoise)
        double noise = pseudoPerlinNoise(noiseX, noiseZ);

        // Apply density factor
        double threshold = 1.0 - config.TRACK_NETWORK_DENSITY;
        return noise > threshold;
    }

    /**
     * Simple pseudo-Perlin noise. In production, use Minecraft's PerlinNoise class.
     */
    private double pseudoPerlinNoise(double x, double z) {
        // Simple sinusoidal noise for now
        return (Math.sin(x * 2.0) * Math.cos(z * 2.0) + 1.0) / 2.0;
    }

    /**
     * Determines if a grid position should have a station.
     */
    private boolean isStationLocation(int gridX, int gridZ) {
        // Stations at grid corners and center
        int halfSpacing = config.STATION_SPACING / 2;

        return (gridX == 0 && gridZ == 0) || // Corner station
               (gridX == halfSpacing && gridZ == 0) || // Mid-edge station
               (gridX == 0 && gridZ == halfSpacing) || // Mid-edge station
               (gridX == halfSpacing && gridZ == halfSpacing); // Center station
    }

    /**
     * Creates a station chunk.
     */
    private TrackNetworkChunk createStationChunk(ChunkPos chunkPos, RandomSource random) {
        // Default track height (will be adjusted based on terrain)
        int height = 70;

        // Determine number of track lanes based on configuration
        int lanes = config.TRACK_LANES;

        return new TrackNetworkChunk(
                TrackNetworkChunk.TrackChunkType.STATION,
                TrackNetworkChunk.TrackDirection.ALL,
                height,
                lanes
        );
    }

    /**
     * Checks if this chunk is on a major route (grid-aligned tracks).
     */
    private TrackNetworkChunk checkMajorRoute(ChunkPos chunkPos, int gridX, int gridZ, RandomSource random) {
        int halfSpacing = config.STATION_SPACING / 2;

        // North-South major route
        if (gridX == 0 || gridX == halfSpacing) {
            return new TrackNetworkChunk(
                    TrackNetworkChunk.TrackChunkType.STRAIGHT_NS,
                    TrackNetworkChunk.TrackDirection.NORTH_SOUTH,
                    70,
                    config.TRACK_LANES
            );
        }

        // East-West major route
        if (gridZ == 0 || gridZ == halfSpacing) {
            return new TrackNetworkChunk(
                    TrackNetworkChunk.TrackChunkType.STRAIGHT_EW,
                    TrackNetworkChunk.TrackDirection.EAST_WEST,
                    70,
                    config.TRACK_LANES
            );
        }

        return null;
    }

    /**
     * Calculates connecting tracks between major routes based on neighboring chunks.
     */
    private TrackNetworkChunk calculateConnectingTrack(ChunkPos chunkPos, RandomSource random) {
        // Check if we should generate a branch connection
        if (random.nextFloat() > config.TRACK_BRANCH_CHANCE) {
            return TrackNetworkChunk.EMPTY;
        }

        // Set flag to prevent infinite recursion when checking neighbors
        isCalculatingConnections.set(true);
        try {
            // Check adjacent chunks for existing tracks
            TrackNetworkChunk north = getTrackChunk(new ChunkPos(chunkPos.x, chunkPos.z - 1));
            TrackNetworkChunk south = getTrackChunk(new ChunkPos(chunkPos.x, chunkPos.z + 1));
            TrackNetworkChunk east = getTrackChunk(new ChunkPos(chunkPos.x + 1, chunkPos.z));
            TrackNetworkChunk west = getTrackChunk(new ChunkPos(chunkPos.x - 1, chunkPos.z));

            boolean connectNorth = north != null && !north.isEmpty() && north.connectsSouth();
            boolean connectSouth = south != null && !south.isEmpty() && south.connectsNorth();
            boolean connectEast = east != null && !east.isEmpty() && east.connectsWest();
            boolean connectWest = west != null && !west.isEmpty() && west.connectsEast();

            // Determine track type based on connections
            if (connectNorth && connectSouth && !connectEast && !connectWest) {
                return new TrackNetworkChunk(
                        TrackNetworkChunk.TrackChunkType.STRAIGHT_NS,
                        TrackNetworkChunk.TrackDirection.NORTH_SOUTH,
                        70,
                        1
                );
            }

            if (connectEast && connectWest && !connectNorth && !connectSouth) {
                return new TrackNetworkChunk(
                        TrackNetworkChunk.TrackChunkType.STRAIGHT_EW,
                        TrackNetworkChunk.TrackDirection.EAST_WEST,
                        70,
                        1
                );
            }

            // Curves
            if (connectNorth && connectEast && !connectSouth && !connectWest) {
                return new TrackNetworkChunk(
                        TrackNetworkChunk.TrackChunkType.CURVE_NE,
                        TrackNetworkChunk.TrackDirection.ALL,
                        70,
                        1
                );
            }

            if (connectNorth && connectWest && !connectSouth && !connectEast) {
                return new TrackNetworkChunk(
                        TrackNetworkChunk.TrackChunkType.CURVE_NW,
                        TrackNetworkChunk.TrackDirection.ALL,
                        70,
                        1
                );
            }

            if (connectSouth && connectEast && !connectNorth && !connectWest) {
                return new TrackNetworkChunk(
                        TrackNetworkChunk.TrackChunkType.CURVE_SE,
                        TrackNetworkChunk.TrackDirection.ALL,
                        70,
                        1
                );
            }

            if (connectSouth && connectWest && !connectNorth && !connectEast) {
                return new TrackNetworkChunk(
                        TrackNetworkChunk.TrackChunkType.CURVE_SW,
                        TrackNetworkChunk.TrackDirection.ALL,
                        70,
                        1
                );
            }

            // Junctions
            int connectionCount = (connectNorth ? 1 : 0) + (connectSouth ? 1 : 0) +
                                  (connectEast ? 1 : 0) + (connectWest ? 1 : 0);

            if (connectionCount >= 3) {
                if (connectionCount == 4) {
                    return new TrackNetworkChunk(
                            TrackNetworkChunk.TrackChunkType.JUNCTION_4WAY,
                            TrackNetworkChunk.TrackDirection.ALL,
                            70,
                            1
                    );
                }

                // 3-way junctions
                if (!connectNorth) {
                    return new TrackNetworkChunk(
                            TrackNetworkChunk.TrackChunkType.JUNCTION_3WAY_S,
                            TrackNetworkChunk.TrackDirection.ALL,
                            70,
                            1
                    );
                } else if (!connectSouth) {
                    return new TrackNetworkChunk(
                            TrackNetworkChunk.TrackChunkType.JUNCTION_3WAY_N,
                            TrackNetworkChunk.TrackDirection.ALL,
                            70,
                            1
                    );
                } else if (!connectEast) {
                    return new TrackNetworkChunk(
                            TrackNetworkChunk.TrackChunkType.JUNCTION_3WAY_W,
                            TrackNetworkChunk.TrackDirection.ALL,
                            70,
                            1
                    );
                } else {
                    return new TrackNetworkChunk(
                            TrackNetworkChunk.TrackChunkType.JUNCTION_3WAY_E,
                            TrackNetworkChunk.TrackDirection.ALL,
                            70,
                            1
                    );
                }
            }

            return TrackNetworkChunk.EMPTY;
        } finally {
            // Always reset the flag
            isCalculatingConnections.set(false);
        }
    }

    /**
     * Debug method to visualize the network.
     */
    public void debugPrintNetwork(int startX, int startZ, int endX, int endZ) {
        System.out.println("Track Network Map:");
        for (int z = startZ; z <= endZ; z++) {
            StringBuilder line = new StringBuilder();
            for (int x = startX; x <= endX; x++) {
                TrackNetworkChunk chunk = getTrackChunk(new ChunkPos(x, z));
                line.append(getDebugChar(chunk)).append(" ");
            }
            System.out.println(line);
        }
    }

    private char getDebugChar(TrackNetworkChunk chunk) {
        if (chunk.isEmpty()) return '.';
        switch (chunk.getType()) {
            case STATION: return 'S';
            case STRAIGHT_NS: return '|';
            case STRAIGHT_EW: return '-';
            case CURVE_NE: return '┘';
            case CURVE_NW: return '└';
            case CURVE_SE: return '┐';
            case CURVE_SW: return '┌';
            case JUNCTION_4WAY: return '┼';
            case JUNCTION_3WAY_N: return '┴';
            case JUNCTION_3WAY_S: return '┬';
            case JUNCTION_3WAY_E: return '├';
            case JUNCTION_3WAY_W: return '┤';
            default: return '?';
        }
    }
}
