package com.vodmordia.trainworld.worldgen.connection;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Processes pending track connections using Create's native track placement system.
 */
public class ConnectionProcessor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Processes a single pending connection.
     * @return true if successfully processed, false if failed
     */
    public static boolean processConnection(ServerLevel level, PendingTrackConnection pending) {
        LOGGER.info("[TrainWorld] Processing connection: {}", pending);

        // Find the actual track positions at chunk boundaries
        BlockPos boundaryTrack1 = findTrackAtChunkBoundary(level, pending.getCurrentChunk(), pending.getDirection());
        BlockPos boundaryTrack2 = findTrackAtChunkBoundary(level, pending.getNeighborChunk(), pending.getDirection().getOpposite());

        if (boundaryTrack1 == null || boundaryTrack2 == null) {
            LOGGER.warn("[TrainWorld] Could not find tracks in chunks, skipping connection (marking as processed to avoid re-queue)");
            return true; // Mark as processed to prevent infinite re-queuing
        }

        int height1 = boundaryTrack1.getY();
        int height2 = boundaryTrack2.getY();
        int elevationChange = Math.abs(height2 - height1);

        // Skip if no elevation change
        if (elevationChange == 0) {
            LOGGER.debug("[TrainWorld] No elevation change, skipping connection");
            return true; // Return true so it's marked as processed
        }

        LOGGER.info("[TrainWorld] Elevation change: {} blocks (heights: {} -> {})",
            elevationChange, height1, height2);

        // Calculate required spacing based on Create's rules
        // From Create's TrackPlacement: minHDistance = max(absAscend < 4 ? absAscend * 4 : absAscend * 3, 6)
        int minHorizontalDistance = Math.max(elevationChange < 4 ? elevationChange * 4 : elevationChange * 3, 6);

        // We want to place connection points this far into each chunk from the boundary
        int offsetPerSide = minHorizontalDistance / 2;

        LOGGER.info("[TrainWorld] Required spacing: {} blocks total ({} blocks per side)",
            minHorizontalDistance, offsetPerSide);

        // Find tracks offset into each chunk from the boundary
        BlockPos connectionPoint1 = findTrackIntoChunk(level, pending.getCurrentChunk(),
                                                        pending.getDirection(), offsetPerSide, height1);
        BlockPos connectionPoint2 = findTrackIntoChunk(level, pending.getNeighborChunk(),
                                                        pending.getDirection().getOpposite(), offsetPerSide, height2);

        if (connectionPoint1 == null || connectionPoint2 == null) {
            LOGGER.warn("[TrainWorld] Could not find tracks at required offset distance, skipping connection");
            return true;
        }

        LOGGER.info("[TrainWorld] Connection points: {} -> {} (distance: {})",
            connectionPoint1, connectionPoint2,
            Math.sqrt(connectionPoint1.distSqr(connectionPoint2)));

        // Use Create's bezier connection system
        boolean success = com.vodmordia.trainworld.worldgen.CreateTrackConnector.connectTracks(
            level,
            connectionPoint1,
            connectionPoint2,
            pending.getDirection()
        );

        if (success) {
            LOGGER.info("[TrainWorld] Successfully created track connection for elevation change of {}", elevationChange);
        } else {
            LOGGER.warn("[TrainWorld] Failed to create track connection (Create's validation rejected it)");
        }

        return true; // Always mark as processed
    }

    /**
     * Finds a track at a specific offset distance into a chunk from the boundary.
     * @param level The server level
     * @param chunkPos The chunk position
     * @param directionFacing The direction facing (INWARD into the chunk from the boundary)
     * @param offsetDistance How many blocks into the chunk to search
     * @param expectedHeight The expected Y coordinate (from boundary track)
     * @return the BlockPos of the track, or null if not found
     */
    private static BlockPos findTrackIntoChunk(ServerLevel level, ChunkPos chunkPos, Direction directionFacing,
                                                int offsetDistance, int expectedHeight) {
        // Get the boundary position
        BlockPos boundaryPos = getChunkBoundaryTrackPos(chunkPos, directionFacing, expectedHeight);

        // Determine search direction (INWARD from the boundary)
        int dx = directionFacing == Direction.EAST ? -1 : (directionFacing == Direction.WEST ? 1 : 0);
        int dz = directionFacing == Direction.SOUTH ? -1 : (directionFacing == Direction.NORTH ? 1 : 0);

        // Search at the specified offset distance
        int x = boundaryPos.getX() + (dx * offsetDistance);
        int z = boundaryPos.getZ() + (dz * offsetDistance);

        // Search for a track at this horizontal position across a range of Y values
        // (the track might not be exactly at expectedHeight due to terrain variation)
        for (int yOffset = -3; yOffset <= 3; yOffset++) {
            BlockPos checkPos = new BlockPos(x, expectedHeight + yOffset, z);
            BlockState state = level.getBlockState(checkPos);

            if (isTrackBlock(state)) {
                var blockEntity = level.getBlockEntity(checkPos);
                boolean hasBE = blockEntity != null;

                LOGGER.info("[TrainWorld] Found track {} blocks into chunk at {} (Y offset: {}), BlockEntity: {}",
                    offsetDistance, checkPos, yOffset, hasBE);

                if (hasBE) {
                    return checkPos;
                }
            }
        }

        LOGGER.warn("[TrainWorld] Could not find track {} blocks into chunk from boundary {} (direction {})",
            offsetDistance, boundaryPos, directionFacing);
        return null;
    }

    /**
     * Finds the actual track position at a chunk boundary.
     * Searches near the boundary in the direction of travel to find the actual track.
     * @return the BlockPos of the track, or null if not found
     */
    private static BlockPos findTrackAtChunkBoundary(ServerLevel level, ChunkPos chunkPos, Direction directionFacing) {
        // Get the boundary position
        BlockPos boundaryPos = getChunkBoundaryTrackPos(chunkPos, directionFacing, 64); // Start at Y=64
        int baseX = boundaryPos.getX();
        int baseZ = boundaryPos.getZ();

        // Determine search direction (we want to search INWARD from the boundary)
        int dx = directionFacing == Direction.EAST ? -1 : (directionFacing == Direction.WEST ? 1 : 0);
        int dz = directionFacing == Direction.SOUTH ? -1 : (directionFacing == Direction.NORTH ? 1 : 0);

        // Search in a small range along the direction of travel (up to 2 blocks inward from boundary)
        for (int offset = 0; offset <= 2; offset++) {
            int x = baseX + (dx * offset);
            int z = baseZ + (dz * offset);

            // First, check the most likely range for tracks (Y 55-85)
            for (int y = 55; y <= 85; y++) {
                BlockPos checkPos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(checkPos);
                if (isTrackBlock(state)) {
                    // Check if there's a track block entity here
                    var blockEntity = level.getBlockEntity(checkPos);
                    boolean hasBE = blockEntity != null;
                    String beType = hasBE ? blockEntity.getClass().getSimpleName() : "none";

                    LOGGER.info("[TrainWorld] Found track block at {} (offset {} from boundary {}), BlockEntity: {}",
                        checkPos, offset, boundaryPos, beType);

                    // Only return if it has a block entity (actual track connection point)
                    if (hasBE) {
                        return checkPos;
                    } else {
                        LOGGER.warn("[TrainWorld] Track at {} has no block entity, continuing search...", checkPos);
                    }
                }
            }

            // If not found, check above (Y 86-150)
            for (int y = 86; y <= 150; y++) {
                BlockPos checkPos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(checkPos);
                if (isTrackBlock(state)) {
                    var blockEntity = level.getBlockEntity(checkPos);
                    boolean hasBE = blockEntity != null;
                    String beType = hasBE ? blockEntity.getClass().getSimpleName() : "none";

                    LOGGER.info("[TrainWorld] Found track block at {} (offset {} from boundary {}), BlockEntity: {}",
                        checkPos, offset, boundaryPos, beType);

                    if (hasBE) {
                        return checkPos;
                    } else {
                        LOGGER.warn("[TrainWorld] Track at {} has no block entity, continuing search...", checkPos);
                    }
                }
            }

            // Finally check below (Y 40-54)
            for (int y = 54; y >= 40; y--) {
                BlockPos checkPos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(checkPos);
                if (isTrackBlock(state)) {
                    var blockEntity = level.getBlockEntity(checkPos);
                    boolean hasBE = blockEntity != null;
                    String beType = hasBE ? blockEntity.getClass().getSimpleName() : "none";

                    LOGGER.info("[TrainWorld] Found track block at {} (offset {} from boundary {}), BlockEntity: {}",
                        checkPos, offset, boundaryPos, beType);

                    if (hasBE) {
                        return checkPos;
                    } else {
                        LOGGER.warn("[TrainWorld] Track at {} has no block entity, continuing search...", checkPos);
                    }
                }
            }
        }

        LOGGER.warn("[TrainWorld] Could not find track near boundary {} in direction {}", boundaryPos, directionFacing);
        return null; // Not found
    }

    /**
     * Checks if a block state is a track block.
     */
    private static boolean isTrackBlock(BlockState state) {
        // Check if it's a Create track block using reflection
        try {
            Class<?> trackBlockClass = Class.forName("com.simibubi.create.content.trains.track.TrackBlock");
            return trackBlockClass.isInstance(state.getBlock());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Gets the track position at the boundary of a chunk in the given direction.
     */
    private static BlockPos getChunkBoundaryTrackPos(ChunkPos chunkPos, Direction direction, int height) {
        BlockPos chunkStart = chunkPos.getWorldPosition();

        return switch (direction) {
            case NORTH -> chunkStart.offset(8, height, 0); // North edge, center of chunk width
            case SOUTH -> chunkStart.offset(8, height, 15); // South edge
            case EAST -> chunkStart.offset(15, height, 8); // East edge
            case WEST -> chunkStart.offset(0, height, 8); // West edge
            default -> chunkStart.offset(8, height, 8); // Center as fallback
        };
    }
}
