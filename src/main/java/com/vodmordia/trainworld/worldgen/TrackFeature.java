package com.vodmordia.trainworld.worldgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.vodmordia.trainworld.worldgen.connection.PendingTrackConnection;
import com.vodmordia.trainworld.worldgen.connection.TrackConnectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.slf4j.Logger;

/**
 * Main world generation feature for Train World.
 * Generates train track networks across the world during terrain generation.
 */
public class TrackFeature extends Feature<NoneFeatureConfiguration> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final TrainWorldConfig config;
    private final TrackNetworkGenerator networkGenerator;
    private final TrainWorldHeightMapper heightMapper;
    private final TrackFeaturePlacement trackPlacement;

    public TrackFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
        this.config = TrainWorldConfig.DEFAULT;
        this.networkGenerator = null; // Will be initialized with world seed
        this.heightMapper = new TrainWorldHeightMapper(config);
        this.trackPlacement = new TrackFeaturePlacement(config);
    }

    public TrackFeature(Codec<NoneFeatureConfiguration> codec, TrainWorldConfig config) {
        super(codec);
        this.config = config;
        this.networkGenerator = null; // Will be initialized with world seed
        this.heightMapper = new TrainWorldHeightMapper(config);
        this.trackPlacement = new TrackFeaturePlacement(config);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!config.TRACKS_ENABLED) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        ChunkPos chunkPos = new ChunkPos(origin);

        // Initialize network generator with world seed if not already done
        TrackNetworkGenerator generator = getOrCreateNetworkGenerator(level.getSeed());

        // Get the chunk
        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);

        // Get track network info for this chunk
        TrackNetworkChunk trackInfo = generator.getTrackChunk(chunkPos);

        if (trackInfo.isEmpty()) {
            return false; // No tracks in this chunk
        }

        // Calculate optimal track height based on terrain
        int trackHeight = heightMapper.calculateOptimalTrackHeight(chunk, trackInfo);

        // Check if we need special handling (bridge/tunnel)
        trackInfo = adjustTrackTypeForTerrain(level, chunk, trackInfo, trackHeight);

        // Place the tracks
        try {
            LOGGER.info("[TrainWorld] Placing {} at chunk {} height {}", trackInfo.getType(), chunkPos, trackHeight);
            trackPlacement.placeTracksInChunk(level, chunk, trackInfo, trackHeight);

            // Mark this chunk as containing tracks so we know to scan it later
            // Use "overworld" as default during worldgen, will be corrected on chunk load
            TrackChunkTracker.markChunkWithTracks("minecraft:overworld", chunkPos);
            LOGGER.info("[TrainWorld] Marked chunk {} as containing tracks", chunkPos);

            // BezierConnections will be created AFTER world load (not during worldgen)
            // This allows us to use Create's proper placement methods with a ServerLevel context
            // For now, just queue the connections - they'll be processed when the world loads
            queuePendingConnections(level, generator, chunkPos, trackInfo, trackHeight);

            return true;
        } catch (Exception e) {
            LOGGER.error("[TrainWorld] Error placing tracks at chunk " + chunkPos + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Queues pending connections for this chunk if it has elevation changes with neighbors.
     * Connections will be processed later by TrackConnectionManager.
     */
    private void queuePendingConnections(WorldGenLevel level, TrackNetworkGenerator generator,
                                          ChunkPos chunkPos, TrackNetworkChunk trackInfo, int trackHeight) {
        // Only queue connections for straight tracks
        if (!trackInfo.getType().name().startsWith("STRAIGHT")) {
            LOGGER.debug("[TrainWorld] Skipping connection queue - not a straight track: {}", trackInfo.getType());
            return;
        }

        // Get the connection manager for this world
        if (!(level instanceof ServerLevel serverLevel)) {
            LOGGER.warn("[TrainWorld] Cannot queue connections during worldgen - level is {} not ServerLevel", level.getClass().getSimpleName());
            return; // Only works on server side
        }

        TrackConnectionManager manager = TrackConnectionManager.getManager(serverLevel);

        // Check each direction the track goes
        if (trackInfo.getDirection().allowsNorth()) {
            queueConnectionInDirection(generator, manager, chunkPos, trackHeight, Direction.NORTH);
        }
        if (trackInfo.getDirection().allowsSouth()) {
            queueConnectionInDirection(generator, manager, chunkPos, trackHeight, Direction.SOUTH);
        }
        if (trackInfo.getDirection().allowsEast()) {
            queueConnectionInDirection(generator, manager, chunkPos, trackHeight, Direction.EAST);
        }
        if (trackInfo.getDirection().allowsWest()) {
            queueConnectionInDirection(generator, manager, chunkPos, trackHeight, Direction.WEST);
        }
    }

    /**
     * Queues a connection in a specific direction if there's an elevation change.
     */
    private void queueConnectionInDirection(TrackNetworkGenerator generator, TrackConnectionManager manager,
                                             ChunkPos currentChunk, int currentHeight, Direction direction) {
        // Calculate neighbor chunk position
        ChunkPos neighborChunk = switch (direction) {
            case NORTH -> new ChunkPos(currentChunk.x, currentChunk.z - 1);
            case SOUTH -> new ChunkPos(currentChunk.x, currentChunk.z + 1);
            case EAST -> new ChunkPos(currentChunk.x + 1, currentChunk.z);
            case WEST -> new ChunkPos(currentChunk.x - 1, currentChunk.z);
            default -> currentChunk;
        };

        // Get neighbor track info (using the generator, so no chunk loading)
        TrackNetworkChunk neighborInfo = generator.getTrackChunk(neighborChunk);
        if (neighborInfo.isEmpty()) {
            return; // No tracks in neighbor
        }

        // Queue the connection with placeholder height
        // The ConnectionProcessor will recalculate actual heights when both chunks are loaded
        // We queue all adjacent straight track chunks - the processor will filter out those without elevation changes
        PendingTrackConnection pending = new PendingTrackConnection(
            currentChunk, neighborChunk, direction, currentHeight, currentHeight // Placeholder - recalculated later
        );

        manager.addPending(pending);
    }


    /**
     * Gets or creates the network generator with the world seed.
     */
    private TrackNetworkGenerator getOrCreateNetworkGenerator(long worldSeed) {
        // In a real implementation, this should be cached per world
        // For now, create a new one (not ideal but functional)
        return new TrackNetworkGenerator(config, worldSeed);
    }

    /**
     * Adjusts track type based on terrain (e.g., convert to bridge or tunnel if needed).
     */
    private TrackNetworkChunk adjustTrackTypeForTerrain(WorldGenLevel level, ChunkAccess chunk,
                                                        TrackNetworkChunk trackInfo, int trackHeight) {
        // Check if tunnel is needed
        if (heightMapper.needsTunnel(chunk, trackInfo)) {
            TrackNetworkChunk.TrackChunkType newType = convertToTunnel(trackInfo.getType());
            if (newType != null) {
                return new TrackNetworkChunk(
                    newType,
                    trackInfo.getDirection(),
                    trackHeight,
                    trackInfo.getTrackLanes()
                );
            }
        }

        // Check if bridge is needed
        // (This would require checking adjacent chunks, simplified for now)
        TrainWorldHeightMapper.ChunkHeightInfo heightInfo = heightMapper.getChunkHeightInfo(chunk);
        if (trackHeight - heightInfo.getAverageHeight() > config.TRACK_BRIDGE_HEIGHT_THRESHOLD) {
            TrackNetworkChunk.TrackChunkType newType = convertToBridge(trackInfo.getType());
            if (newType != null) {
                return new TrackNetworkChunk(
                    newType,
                    trackInfo.getDirection(),
                    trackHeight,
                    trackInfo.getTrackLanes()
                );
            }
        }

        return trackInfo;
    }

    /**
     * Converts a track type to its tunnel equivalent.
     */
    private TrackNetworkChunk.TrackChunkType convertToTunnel(TrackNetworkChunk.TrackChunkType type) {
        switch (type) {
            case STRAIGHT_NS:
                return TrackNetworkChunk.TrackChunkType.TUNNEL_NS;
            case STRAIGHT_EW:
                return TrackNetworkChunk.TrackChunkType.TUNNEL_EW;
            default:
                return null; // Can't tunnel curves/junctions easily
        }
    }

    /**
     * Converts a track type to its bridge equivalent.
     */
    private TrackNetworkChunk.TrackChunkType convertToBridge(TrackNetworkChunk.TrackChunkType type) {
        switch (type) {
            case STRAIGHT_NS:
                return TrackNetworkChunk.TrackChunkType.BRIDGE_NS;
            case STRAIGHT_EW:
                return TrackNetworkChunk.TrackChunkType.BRIDGE_EW;
            default:
                return null; // Bridges only for straight tracks
        }
    }

}
