package com.vodmordia.trainworld.worldgen;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Represents the track network information for a single chunk.
 * Inspired by LostCities' Railway system.
 */
public class TrackNetworkChunk {

    /**
     * Types of track chunks in the network.
     */
    public enum TrackChunkType {
        NONE,                    // No tracks
        STRAIGHT_NS,             // North-South straight track
        STRAIGHT_EW,             // East-West straight track
        CURVE_NE,                // Curve from North to East
        CURVE_NW,                // Curve from North to West
        CURVE_SE,                // Curve from South to East
        CURVE_SW,                // Curve from South to West
        JUNCTION_3WAY_N,         // T-junction opening to North
        JUNCTION_3WAY_S,         // T-junction opening to South
        JUNCTION_3WAY_E,         // T-junction opening to East
        JUNCTION_3WAY_W,         // T-junction opening to West
        JUNCTION_4WAY,           // Cross junction (all 4 directions)
        STATION,                 // Station chunk
        BRIDGE_NS,               // Bridge going North-South
        BRIDGE_EW,               // Bridge going East-West
        TUNNEL_NS,               // Tunnel going North-South
        TUNNEL_EW,               // Tunnel going East-West
        INCLINE_UP_N,            // Incline going up to the North
        INCLINE_UP_S,            // Incline going up to the South
        INCLINE_UP_E,            // Incline going up to the East
        INCLINE_UP_W,            // Incline going up to the West
        INCLINE_DOWN_N,          // Incline going down to the North
        INCLINE_DOWN_S,          // Incline going down to the South
        INCLINE_DOWN_E,          // Incline going down to the East
        INCLINE_DOWN_W;          // Incline going down to the West

        public boolean isStation() {
            return this == STATION;
        }

        public boolean isBridge() {
            return this == BRIDGE_NS || this == BRIDGE_EW;
        }

        public boolean isTunnel() {
            return this == TUNNEL_NS || this == TUNNEL_EW;
        }

        public boolean isIncline() {
            return name().startsWith("INCLINE_");
        }

        public boolean isJunction() {
            return name().startsWith("JUNCTION_");
        }

        public boolean isStraight() {
            return this == STRAIGHT_NS || this == STRAIGHT_EW;
        }

        public boolean isCurve() {
            return name().startsWith("CURVE_");
        }
    }

    /**
     * Direction of track flow through the chunk.
     */
    public enum TrackDirection {
        NORTH,
        SOUTH,
        EAST,
        WEST,
        NORTH_SOUTH,     // Bidirectional
        EAST_WEST,       // Bidirectional
        ALL;             // All directions (for junctions)

        public boolean allowsNorth() {
            return this == NORTH || this == NORTH_SOUTH || this == ALL;
        }

        public boolean allowsSouth() {
            return this == SOUTH || this == NORTH_SOUTH || this == ALL;
        }

        public boolean allowsEast() {
            return this == EAST || this == EAST_WEST || this == ALL;
        }

        public boolean allowsWest() {
            return this == WEST || this == EAST_WEST || this == ALL;
        }

        public boolean isBidirectional() {
            return this == NORTH_SOUTH || this == EAST_WEST || this == ALL;
        }
    }

    private final TrackChunkType type;
    private final TrackDirection direction;
    private final int trackHeight;
    private final int trackLanes;
    private final List<BlockPos> stationPlatforms;

    /**
     * Empty chunk with no tracks.
     */
    public static final TrackNetworkChunk EMPTY = new TrackNetworkChunk(
            TrackChunkType.NONE,
            TrackDirection.ALL,
            0,
            0,
            List.of()
    );

    public TrackNetworkChunk(TrackChunkType type, TrackDirection direction, int trackHeight, int trackLanes) {
        this(type, direction, trackHeight, trackLanes, List.of());
    }

    public TrackNetworkChunk(TrackChunkType type, TrackDirection direction, int trackHeight, int trackLanes, List<BlockPos> stationPlatforms) {
        this.type = type;
        this.direction = direction;
        this.trackHeight = trackHeight;
        this.trackLanes = trackLanes;
        this.stationPlatforms = stationPlatforms;
    }

    public TrackChunkType getType() {
        return type;
    }

    public TrackDirection getDirection() {
        return direction;
    }

    public int getTrackHeight() {
        return trackHeight;
    }

    public int getTrackLanes() {
        return trackLanes;
    }

    public List<BlockPos> getStationPlatforms() {
        return stationPlatforms;
    }

    public boolean isEmpty() {
        return type == TrackChunkType.NONE;
    }

    public boolean connectsNorth() {
        return direction.allowsNorth() &&
                (type == TrackChunkType.STRAIGHT_NS ||
                 type == TrackChunkType.CURVE_NE ||
                 type == TrackChunkType.CURVE_NW ||
                 type.isJunction() ||
                 type == TrackChunkType.INCLINE_UP_N ||
                 type == TrackChunkType.INCLINE_DOWN_N ||
                 type == TrackChunkType.BRIDGE_NS ||
                 type == TrackChunkType.TUNNEL_NS);
    }

    public boolean connectsSouth() {
        return direction.allowsSouth() &&
                (type == TrackChunkType.STRAIGHT_NS ||
                 type == TrackChunkType.CURVE_SE ||
                 type == TrackChunkType.CURVE_SW ||
                 type.isJunction() ||
                 type == TrackChunkType.INCLINE_UP_S ||
                 type == TrackChunkType.INCLINE_DOWN_S ||
                 type == TrackChunkType.BRIDGE_NS ||
                 type == TrackChunkType.TUNNEL_NS);
    }

    public boolean connectsEast() {
        return direction.allowsEast() &&
                (type == TrackChunkType.STRAIGHT_EW ||
                 type == TrackChunkType.CURVE_NE ||
                 type == TrackChunkType.CURVE_SE ||
                 type.isJunction() ||
                 type == TrackChunkType.INCLINE_UP_E ||
                 type == TrackChunkType.INCLINE_DOWN_E ||
                 type == TrackChunkType.BRIDGE_EW ||
                 type == TrackChunkType.TUNNEL_EW);
    }

    public boolean connectsWest() {
        return direction.allowsWest() &&
                (type == TrackChunkType.STRAIGHT_EW ||
                 type == TrackChunkType.CURVE_NW ||
                 type == TrackChunkType.CURVE_SW ||
                 type.isJunction() ||
                 type == TrackChunkType.INCLINE_UP_W ||
                 type == TrackChunkType.INCLINE_DOWN_W ||
                 type == TrackChunkType.BRIDGE_EW ||
                 type == TrackChunkType.TUNNEL_EW);
    }

    @Override
    public String toString() {
        return "TrackNetworkChunk{" +
                "type=" + type +
                ", direction=" + direction +
                ", height=" + trackHeight +
                ", lanes=" + trackLanes +
                '}';
    }
}
