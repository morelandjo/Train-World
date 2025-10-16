package com.vodmordia.trainworld.worldgen;

import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which chunks contain train tracks.
 * This allows us to only scan chunks with tracks instead of all chunks.
 */
public class TrackChunkTracker {

    // Track chunks per dimension
    private static final ConcurrentHashMap<String, Set<Long>> CHUNKS_WITH_TRACKS = new ConcurrentHashMap<>();

    /**
     * Marks a chunk as containing tracks.
     */
    public static void markChunkWithTracks(String dimensionKey, ChunkPos chunkPos) {
        Set<Long> chunks = CHUNKS_WITH_TRACKS.computeIfAbsent(dimensionKey, k -> ConcurrentHashMap.newKeySet());
        chunks.add(chunkPos.toLong());
    }

    /**
     * Checks if a chunk has tracks.
     */
    public static boolean hasTrackChunk(String dimensionKey, ChunkPos chunkPos) {
        Set<Long> chunks = CHUNKS_WITH_TRACKS.get(dimensionKey);
        return chunks != null && chunks.contains(chunkPos.toLong());
    }

    /**
     * Clears all tracked chunks for a dimension.
     */
    public static void clearDimension(String dimensionKey) {
        CHUNKS_WITH_TRACKS.remove(dimensionKey);
    }

    /**
     * Gets statistics about tracked chunks.
     */
    public static int getTrackedChunkCount(String dimensionKey) {
        Set<Long> chunks = CHUNKS_WITH_TRACKS.get(dimensionKey);
        return chunks != null ? chunks.size() : 0;
    }
}
