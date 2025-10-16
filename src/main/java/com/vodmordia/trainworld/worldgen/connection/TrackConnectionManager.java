package com.vodmordia.trainworld.worldgen.connection;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages pending track connections for a world.
 * Processes connections incrementally to avoid performance issues.
 */
public class TrackConnectionManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, TrackConnectionManager> WORLD_MANAGERS = new ConcurrentHashMap<>();

    private final Queue<PendingTrackConnection> pendingQueue;
    private final Set<String> processedConnections;
    private final Set<String> pendingIds; // Track what's already queued
    private int processedThisTick = 0;
    private int totalProcessed = 0;
    private int totalAdded = 0;

    private TrackConnectionManager() {
        this.pendingQueue = new ConcurrentLinkedQueue<>();
        this.processedConnections = ConcurrentHashMap.newKeySet();
        this.pendingIds = ConcurrentHashMap.newKeySet();
    }

    /**
     * Gets or creates the manager for a specific world.
     */
    public static TrackConnectionManager getManager(ServerLevel level) {
        String worldKey = level.dimension().location().toString();
        return WORLD_MANAGERS.computeIfAbsent(worldKey, k -> new TrackConnectionManager());
    }

    /**
     * Clears the manager for a world (called on world unload).
     */
    public static void clearManager(ServerLevel level) {
        String worldKey = level.dimension().location().toString();
        TrackConnectionManager manager = WORLD_MANAGERS.remove(worldKey);
        if (manager != null) {
            LOGGER.info("[TrainWorld] Cleared connection manager for {}: processed={}, pending={}",
                worldKey, manager.totalProcessed, manager.pendingQueue.size());
        }
    }

    /**
     * Adds a pending connection to the queue if not already processed or queued.
     */
    public void addPending(PendingTrackConnection connection) {
        String id = connection.getUniqueId();

        // Check if already processed
        if (processedConnections.contains(id)) {
            return;
        }

        // Check if already in queue
        if (!pendingIds.add(id)) {
            return; // Already queued
        }

        pendingQueue.offer(connection);
        totalAdded++;

        if (totalAdded % 100 == 0) {
            LOGGER.info("[TrainWorld] Connection queue stats: added={}, processed={}, pending={}",
                totalAdded, totalProcessed, pendingQueue.size());
        }
    }

    /**
     * Processes a batch of pending connections.
     * @param level The server level to process connections in
     * @param batchSize Maximum number of connections to process this tick
     * @return Number of connections actually processed
     */
    public int processBatch(ServerLevel level, int batchSize) {
        processedThisTick = 0;

        for (int i = 0; i < batchSize && !pendingQueue.isEmpty(); i++) {
            PendingTrackConnection pending = pendingQueue.poll();
            if (pending == null) {
                break;
            }

            // Remove from pending IDs
            pendingIds.remove(pending.getUniqueId());

            // Check if both chunks are loaded
            if (!level.hasChunk(pending.getCurrentChunk().x, pending.getCurrentChunk().z) ||
                !level.hasChunk(pending.getNeighborChunk().x, pending.getNeighborChunk().z)) {
                // Re-queue if chunks aren't loaded yet
                // But only if not too old (avoid infinite re-queuing)
                long age = System.currentTimeMillis() - pending.getTimestamp();
                if (age < 60000) { // 60 seconds max age
                    pendingIds.add(pending.getUniqueId());
                    pendingQueue.offer(pending);
                } else {
                    LOGGER.warn("[TrainWorld] Dropping old pending connection: {}", pending);
                }
                continue;
            }

            // Process the connection
            try {
                boolean success = ConnectionProcessor.processConnection(level, pending);

                // ALWAYS mark as processed to prevent infinite re-queuing
                // Connections that fail due to missing chunks will be queued again
                // when those chunks load (via ChunkEvent.Load)
                processedConnections.add(pending.getUniqueId());

                if (success) {
                    totalProcessed++;
                    processedThisTick++;

                    if (totalProcessed % 50 == 0) {
                        LOGGER.info("[TrainWorld] Processed {} connections total, {} this tick",
                            totalProcessed, processedThisTick);
                    }
                } else {
                    LOGGER.debug("[TrainWorld] Connection not created (marked as processed): {}", pending);
                }
            } catch (Exception e) {
                LOGGER.error("[TrainWorld] Error processing connection: " + pending, e);
                // Mark as processed even on exception to avoid infinite retry
                processedConnections.add(pending.getUniqueId());
            }
        }

        return processedThisTick;
    }

    /**
     * Checks if a connection has been processed.
     */
    public boolean isProcessed(String uniqueId) {
        return processedConnections.contains(uniqueId);
    }

    /**
     * Gets the number of pending connections.
     */
    public int getPendingCount() {
        return pendingQueue.size();
    }

    /**
     * Gets statistics about this manager.
     */
    public String getStats() {
        return String.format("TrackConnectionManager[added=%d, processed=%d, pending=%d]",
            totalAdded, totalProcessed, pendingQueue.size());
    }
}
