package com.vodmordia.trainworld.events;

import com.mojang.logging.LogUtils;
import com.vodmordia.trainworld.worldgen.*;
import com.vodmordia.trainworld.worldgen.connection.PendingTrackConnection;
import com.vodmordia.trainworld.worldgen.connection.TrackConnectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles events related to track connection processing.
 * Processes pending connections incrementally during server ticks.
 */
@EventBusSubscriber
public class TrackConnectionEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONNECTIONS_PER_TICK = 1; // Process only 1 connection per tick to avoid lag
    private static final int CONNECTIONS_ON_WORLD_LOAD = 50; // Process up to 50 connections when world loads
    private static final Set<String> processedWorlds = new HashSet<>(); // Track which worlds have been processed

    /**
     * Processes ALL pending track connections when the server starts.
     * This ensures that all queued connections from worldgen are processed immediately
     * using Create's proper placement methods with ServerLevel context.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("[TrainWorld] ===== SERVER STARTED - PROCESSING ALL QUEUED BEZIER CONNECTIONS =====");

        // Process connections for each world
        for (ServerLevel level : event.getServer().getAllLevels()) {
            String dimensionKey = level.dimension().location().toString();

            // Skip if already processed (in case of reload)
            if (processedWorlds.contains(dimensionKey)) {
                LOGGER.info("[TrainWorld] World {} already processed, skipping", dimensionKey);
                continue;
            }

            TrackConnectionManager manager = TrackConnectionManager.getManager(level);
            int pendingCount = manager.getPendingCount();

            if (pendingCount > 0) {
                LOGGER.info("[TrainWorld] World {} has {} pending connections, processing in batches", dimensionKey, pendingCount);

                // Process in batches to avoid server freeze
                int totalProcessed = 0;
                int maxBatches = 100; // Safety limit: max 10,000 connections (100 batches * 100 per batch)

                for (int batch = 0; batch < maxBatches && manager.getPendingCount() > 0; batch++) {
                    int processed = manager.processBatch(level, CONNECTIONS_ON_WORLD_LOAD);
                    totalProcessed += processed;

                    if (processed == 0) {
                        break; // No more connections to process
                    }
                }

                LOGGER.info("[TrainWorld] World {} finished: processed {} total connections", dimensionKey, totalProcessed);
            } else {
                LOGGER.info("[TrainWorld] World {} has no pending connections", dimensionKey);
            }

            processedWorlds.add(dimensionKey);
        }

        LOGGER.info("[TrainWorld] ===== BEZIER CONNECTION PROCESSING COMPLETE =====");
    }

    /**
     * Processes pending track connections each server tick.
     * This is called after all other tick logic has completed.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Process connections for each world
        for (ServerLevel level : event.getServer().getAllLevels()) {
            TrackConnectionManager manager = TrackConnectionManager.getManager(level);
            manager.processBatch(level, CONNECTIONS_PER_TICK);
        }
    }

    /**
     * Queues track connections when a chunk is loaded.
     * This scans for tracks at chunk boundaries and queues connections if elevation changes exist.
     *
     * NOTE: Only fires for non-worldgen chunk loads to avoid interfering with spawn preparation.
     */
    private static final Set<ChunkPos> scannedChunks = new HashSet<>(); // Track which chunks have been scanned
    private static final int MAX_SCANNED_CHUNKS_CACHE = 10000; // Prevent memory issues

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Skip during worldgen to avoid interfering with spawn preparation
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return; // Only process on server side
        }

        ChunkPos chunkPos = event.getChunk().getPos();

        // Skip if this chunk was already scanned
        if (scannedChunks.contains(chunkPos)) {
            return;
        }

        // OPTIMIZATION: Only scan chunks that contain tracks
        String dimensionKey = serverLevel.dimension().location().toString();
        boolean hasTracksInChunk = TrackChunkTracker.hasTrackChunk(dimensionKey, chunkPos);

        if (!hasTracksInChunk) {
            // This chunk has no tracks, skip it entirely
            return;
        }

        // Mark this chunk as scanned BEFORE processing to prevent re-entry
        // Clear cache if it gets too large
        if (scannedChunks.size() >= MAX_SCANNED_CHUNKS_CACHE) {
            LOGGER.warn("[TrainWorld] Scanned chunks cache full ({}), clearing oldest entries", MAX_SCANNED_CHUNKS_CACHE);
            scannedChunks.clear();
        }
        scannedChunks.add(chunkPos);

        LOGGER.info("[TrainWorld] >>> Chunk {} has tracks, scanning for connections", chunkPos);

        // Defer connection queuing to avoid cascading issues
        serverLevel.getServer().execute(() -> {
            try {
                LOGGER.info("[TrainWorld] Starting deferred scan for chunk {}", chunkPos);
                TrackConnectionManager manager = TrackConnectionManager.getManager(serverLevel);

                // Check all four cardinal directions for tracks at chunk boundaries
                LOGGER.debug("[TrainWorld] Checking NORTH for chunk {}", chunkPos);
                queueConnectionsInDirection(serverLevel, manager, chunkPos, Direction.NORTH);
                LOGGER.debug("[TrainWorld] Checking SOUTH for chunk {}", chunkPos);
                queueConnectionsInDirection(serverLevel, manager, chunkPos, Direction.SOUTH);
                LOGGER.debug("[TrainWorld] Checking EAST for chunk {}", chunkPos);
                queueConnectionsInDirection(serverLevel, manager, chunkPos, Direction.EAST);
                LOGGER.debug("[TrainWorld] Checking WEST for chunk {}", chunkPos);
                queueConnectionsInDirection(serverLevel, manager, chunkPos, Direction.WEST);
                LOGGER.info("[TrainWorld] Completed deferred scan for chunk {}", chunkPos);
            } catch (Exception e) {
                LOGGER.error("[TrainWorld] Error queuing connections for chunk {}: {}", chunkPos, e.getMessage(), e);
            }
        });
    }

    /**
     * Checks if a chunk is fully loaded and ready for block access.
     * This prevents triggering cascading chunk loads or worldgen hangs.
     */
    private static boolean isChunkFullyLoaded(ServerLevel level, ChunkPos chunkPos) {
        LOGGER.info("[TrainWorld] [isChunkFullyLoaded] Checking chunk {} - about to call getChunkSource().getChunkNow()", chunkPos);

        // Use getChunkNow which NEVER triggers chunk loading
        var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);

        if (chunk == null) {
            LOGGER.info("[TrainWorld] [isChunkFullyLoaded] Chunk {} returned null from getChunkNow", chunkPos);
            return false;
        }

        // Check if chunk is a LevelChunk (fully loaded) not just a ChunkAccess
        boolean isLevelChunk = chunk instanceof net.minecraft.world.level.chunk.LevelChunk;
        LOGGER.info("[TrainWorld] [isChunkFullyLoaded] Chunk {} is LevelChunk: {}", chunkPos, isLevelChunk);

        return isLevelChunk;
    }

    /**
     * Checks if there are tracks at the chunk boundary in the given direction and queues a connection.
     * IMPORTANT: Only checks already-loaded chunks, never triggers chunk loads.
     */
    private static void queueConnectionsInDirection(ServerLevel level, TrackConnectionManager manager,
                                                     ChunkPos chunkPos, Direction direction) {
        try {
            LOGGER.info("[TrainWorld] [STEP 1] Checking direction {} from chunk {}", direction, chunkPos);

            // Calculate neighbor chunk position FIRST (before any chunk access)
            ChunkPos neighborChunk = getNeighborChunk(chunkPos, direction);
            LOGGER.info("[TrainWorld] [STEP 2] Neighbor chunk: {}", neighborChunk);

            // CRITICAL: Check if neighbor chunk is FULLY loaded BEFORE accessing blocks
            // This prevents cascading chunk loads and worldgen hangs
            LOGGER.info("[TrainWorld] [STEP 3] Checking if neighbor chunk {} is loaded", neighborChunk);
            if (!isChunkFullyLoaded(level, neighborChunk)) {
                LOGGER.info("[TrainWorld] [STEP 3 RESULT] Neighbor chunk {} not fully loaded, skipping", neighborChunk);
                return; // Neighbor not loaded yet, will be queued when it loads
            }
            LOGGER.info("[TrainWorld] [STEP 3 RESULT] Neighbor chunk {} IS fully loaded", neighborChunk);

            // PERFORMANCE: Check if neighbor has tracks before expensive Y-search
            String dimensionKey = level.dimension().location().toString();
            LOGGER.info("[TrainWorld] [STEP 4] Checking if neighbor chunk {} has tracks (dimension={})", neighborChunk, dimensionKey);
            if (!TrackChunkTracker.hasTrackChunk(dimensionKey, neighborChunk)) {
                LOGGER.info("[TrainWorld] [STEP 4 RESULT] Neighbor chunk {} has no tracks, skipping", neighborChunk);
                return; // Neighbor has no tracks, skip entirely
            }
            LOGGER.info("[TrainWorld] [STEP 4 RESULT] Neighbor chunk {} has tracks", neighborChunk);

            LOGGER.info("[TrainWorld] [STEP 5] Finding track at boundary for chunk {}, direction {}", chunkPos, direction);

            // Get the boundary position
            BlockPos boundaryPos = getChunkBoundaryPos(chunkPos, direction);
            LOGGER.info("[TrainWorld] [STEP 5.1] Boundary position: {}", boundaryPos);

            // Now safe to search for tracks in current chunk
            LOGGER.info("[TrainWorld] [STEP 6] Calling findTrackAtBoundary for current chunk boundary {}", boundaryPos);
            int trackHeight = findTrackAtBoundary(level, boundaryPos);
            LOGGER.info("[TrainWorld] [STEP 6 RESULT] findTrackAtBoundary returned: {}", trackHeight);
            if (trackHeight == -1) {
                LOGGER.info("[TrainWorld] No track found at boundary {} for chunk {}", boundaryPos, chunkPos);
                return; // No track at this boundary
            }

            LOGGER.info("[TrainWorld] [STEP 7] Found track at height {} in chunk {}, checking neighbor", trackHeight, chunkPos);

            // Find track in neighbor (already verified it's loaded)
            BlockPos neighborBoundaryPos = getChunkBoundaryPos(neighborChunk, direction.getOpposite());
            LOGGER.info("[TrainWorld] [STEP 7.1] Neighbor boundary position: {}", neighborBoundaryPos);

            LOGGER.info("[TrainWorld] [STEP 8] Calling findTrackAtBoundary for neighbor chunk boundary {}", neighborBoundaryPos);
            int neighborHeight = findTrackAtBoundary(level, neighborBoundaryPos);
            LOGGER.info("[TrainWorld] [STEP 8 RESULT] findTrackAtBoundary returned: {}", neighborHeight);
            if (neighborHeight == -1) {
                LOGGER.info("[TrainWorld] No track found in neighbor chunk {}", neighborChunk);
                return; // No track in neighbor
            }

            LOGGER.info("[TrainWorld] [STEP 9] Found neighbor track at height {}, checking elevation change", neighborHeight);

            // PERFORMANCE: Skip if no elevation change
            int elevationChange = Math.abs(neighborHeight - trackHeight);
            if (elevationChange == 0) {
                LOGGER.info("[TrainWorld] No elevation change between {} and {}, skipping", chunkPos, neighborChunk);
                return;
            }

            LOGGER.info("[TrainWorld] [STEP 10] Creating pending connection");
            // Queue the connection
            PendingTrackConnection pending = new PendingTrackConnection(
                chunkPos, neighborChunk, direction, trackHeight, neighborHeight
            );

            LOGGER.info("[TrainWorld] [STEP 11] Checking if connection already processed");
            // Check if this connection was already processed
            if (manager.isProcessed(pending.getUniqueId())) {
                LOGGER.info("[TrainWorld] Connection already processed, skipping: {}", pending);
                return;
            }

            LOGGER.info("[TrainWorld] [STEP 12] QUEUED: {}", pending);
            manager.addPending(pending);
            LOGGER.info("[TrainWorld] [STEP 13] Successfully queued connection from {} to {}", chunkPos, neighborChunk);
        } catch (Exception e) {
            LOGGER.error("[TrainWorld] ERROR in queueConnectionsInDirection for chunk {} direction {}: {}",
                chunkPos, direction, e.getMessage(), e);
        }
    }

    /**
     * Gets the chunk boundary position for a given direction.
     */
    private static BlockPos getChunkBoundaryPos(ChunkPos chunkPos, Direction direction) {
        BlockPos chunkStart = chunkPos.getWorldPosition();
        return switch (direction) {
            case NORTH -> chunkStart.offset(8, 64, 0);
            case SOUTH -> chunkStart.offset(8, 64, 15);
            case EAST -> chunkStart.offset(15, 64, 8);
            case WEST -> chunkStart.offset(0, 64, 8);
            default -> chunkStart.offset(8, 64, 8);
        };
    }

    /**
     * Gets the neighbor chunk position in the given direction.
     */
    private static ChunkPos getNeighborChunk(ChunkPos chunkPos, Direction direction) {
        return switch (direction) {
            case NORTH -> new ChunkPos(chunkPos.x, chunkPos.z - 1);
            case SOUTH -> new ChunkPos(chunkPos.x, chunkPos.z + 1);
            case EAST -> new ChunkPos(chunkPos.x + 1, chunkPos.z);
            case WEST -> new ChunkPos(chunkPos.x - 1, chunkPos.z);
            default -> chunkPos;
        };
    }

    /**
     * Finds a track at the given boundary position by searching vertically.
     * Optimized to search likely Y ranges first (60-80 for tracks).
     * @return the Y coordinate of the track, or -1 if not found
     */
    private static int findTrackAtBoundary(ServerLevel level, BlockPos boundaryPos) {
        LOGGER.info("[TrainWorld] [findTrack] Searching for track at boundary {}", boundaryPos);
        int x = boundaryPos.getX();
        int z = boundaryPos.getZ();

        LOGGER.info("[TrainWorld] [findTrack] Checking Y range 55-85");
        // First, check the most likely range for tracks (Y 55-85)
        for (int y = 55; y <= 85; y++) {
            BlockPos checkPos = new BlockPos(x, y, z);
            LOGGER.info("[TrainWorld] [findTrack] Checking position {} (about to call getBlockState)", checkPos);
            BlockState state = level.getBlockState(checkPos);
            LOGGER.info("[TrainWorld] [findTrack] Got block state for {}: {}", checkPos, state.getBlock().getName().getString());
            if (isTrackBlock(state)) {
                LOGGER.info("[TrainWorld] [findTrack] Found track at Y={}", y);
                return y;
            }
        }

        LOGGER.info("[TrainWorld] [findTrack] Checking Y range 86-150");
        // If not found, check above (Y 86-150)
        for (int y = 86; y <= 150; y++) {
            BlockPos checkPos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(checkPos);
            if (isTrackBlock(state)) {
                LOGGER.info("[TrainWorld] [findTrack] Found track at Y={}", y);
                return y;
            }
        }

        LOGGER.info("[TrainWorld] [findTrack] Checking Y range 54-40");
        // Finally check below (Y 40-54)
        for (int y = 54; y >= 40; y--) {
            BlockPos checkPos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(checkPos);
            if (isTrackBlock(state)) {
                LOGGER.info("[TrainWorld] [findTrack] Found track at Y={}", y);
                return y;
            }
        }

        LOGGER.info("[TrainWorld] [findTrack] No track found at boundary {}", boundaryPos);
        return -1; // No track found
    }

    /**
     * Checks if a block state is a Create track block.
     */
    private static boolean isTrackBlock(BlockState state) {
        try {
            Class<?> trackBlockClass = Class.forName("com.simibubi.create.content.trains.track.TrackBlock");
            return trackBlockClass.isInstance(state.getBlock());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Cleans up connection managers when a world unloads.
     */
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            String dimensionKey = serverLevel.dimension().location().toString();

            TrackConnectionManager.clearManager(serverLevel);
            TrackChunkTracker.clearDimension(dimensionKey);
            scannedChunks.clear(); // Clear the scanned chunks set
            processedWorlds.remove(dimensionKey); // Allow re-processing on reload

            LOGGER.info("[TrainWorld] Cleaned up connection manager, chunk tracker, and scan cache for world unload");
        }
    }
}
