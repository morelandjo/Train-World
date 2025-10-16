package com.vodmordia.trainworld.worldgen.connection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * Represents a track connection that needs to be processed after chunks are loaded.
 * Stores the information needed to create a proper BezierConnection with correct spacing.
 */
public class PendingTrackConnection {

    private final ChunkPos currentChunk;
    private final ChunkPos neighborChunk;
    private final Direction direction;
    private final int currentHeight;
    private final int neighborHeight;
    private final long timestamp;

    public PendingTrackConnection(ChunkPos currentChunk, ChunkPos neighborChunk, Direction direction,
                                   int currentHeight, int neighborHeight) {
        this.currentChunk = currentChunk;
        this.neighborChunk = neighborChunk;
        this.direction = direction;
        this.currentHeight = currentHeight;
        this.neighborHeight = neighborHeight;
        this.timestamp = System.currentTimeMillis();
    }

    public ChunkPos getCurrentChunk() {
        return currentChunk;
    }

    public ChunkPos getNeighborChunk() {
        return neighborChunk;
    }

    public Direction getDirection() {
        return direction;
    }

    public int getCurrentHeight() {
        return currentHeight;
    }

    public int getNeighborHeight() {
        return neighborHeight;
    }

    public int getElevationChange() {
        return Math.abs(neighborHeight - currentHeight);
    }

    public boolean isGoingUp() {
        return neighborHeight > currentHeight;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Creates a unique identifier for this connection to avoid duplicate processing.
     * Uses the lower chunk position as the primary to ensure both directions have the same ID.
     */
    public String getUniqueId() {
        ChunkPos lower, higher;
        if (currentChunk.x < neighborChunk.x ||
            (currentChunk.x == neighborChunk.x && currentChunk.z < neighborChunk.z)) {
            lower = currentChunk;
            higher = neighborChunk;
        } else {
            lower = neighborChunk;
            higher = currentChunk;
        }
        return String.format("%d,%d->%d,%d", lower.x, lower.z, higher.x, higher.z);
    }

    @Override
    public String toString() {
        return String.format("PendingConnection[%s -> %s, dir=%s, heights=%d->%d, change=%d]",
            currentChunk, neighborChunk, direction, currentHeight, neighborHeight, getElevationChange());
    }
}
