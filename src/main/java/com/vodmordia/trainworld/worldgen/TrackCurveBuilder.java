package com.vodmordia.trainworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds curved track sections following Create's curve rules:
 * - Curves can be 45° or 90° only
 * - Minimum radius of 8 blocks
 * - Must begin and end with aligned tracks
 */
public class TrackCurveBuilder {

    private final TrainWorldConfig config;

    public TrackCurveBuilder(TrainWorldConfig config) {
        this.config = config;
    }

    /**
     * Represents a curve segment.
     */
    public static class CurveSegment {
        public final BlockPos position;
        public final CurveAngle angle;
        public final int radius;

        public CurveSegment(BlockPos position, CurveAngle angle, int radius) {
            this.position = position;
            this.angle = angle;
            this.radius = radius;
        }
    }

    /**
     * Valid curve angles per Create's rules.
     */
    public enum CurveAngle {
        CURVE_45(45),
        CURVE_90(90);

        private final int degrees;

        CurveAngle(int degrees) {
            this.degrees = degrees;
        }

        public int getDegrees() {
            return degrees;
        }
    }

    /**
     * Curve direction combinations.
     */
    public enum CurveType {
        NORTH_TO_EAST,
        NORTH_TO_WEST,
        SOUTH_TO_EAST,
        SOUTH_TO_WEST,
        EAST_TO_NORTH,
        EAST_TO_SOUTH,
        WEST_TO_NORTH,
        WEST_TO_SOUTH
    }

    /**
     * Calculates positions for a 90-degree curve.
     */
    public List<BlockPos> calculate90DegreeCurve(BlockPos start, CurveType curveType, int radius) {
        // Ensure minimum radius
        radius = Math.max(radius, config.TRACK_MIN_CURVE_RADIUS);

        List<BlockPos> positions = new ArrayList<>();

        // Calculate curve based on type
        switch (curveType) {
            case NORTH_TO_EAST:
                positions = calculateQuarterCircle(start, radius, 0, 90);
                break;
            case NORTH_TO_WEST:
                positions = calculateQuarterCircle(start, radius, 90, 180);
                break;
            case SOUTH_TO_EAST:
                positions = calculateQuarterCircle(start, radius, 270, 360);
                break;
            case SOUTH_TO_WEST:
                positions = calculateQuarterCircle(start, radius, 180, 270);
                break;
            case EAST_TO_NORTH:
                positions = calculateQuarterCircle(start, radius, 0, 90);
                break;
            case EAST_TO_SOUTH:
                positions = calculateQuarterCircle(start, radius, 270, 360);
                break;
            case WEST_TO_NORTH:
                positions = calculateQuarterCircle(start, radius, 90, 180);
                break;
            case WEST_TO_SOUTH:
                positions = calculateQuarterCircle(start, radius, 180, 270);
                break;
        }

        return positions;
    }

    /**
     * Calculates positions for a 45-degree curve.
     */
    public List<BlockPos> calculate45DegreeCurve(BlockPos start, CurveType curveType, int radius) {
        // Ensure minimum radius
        radius = Math.max(radius, config.TRACK_MIN_CURVE_RADIUS);

        List<BlockPos> positions = new ArrayList<>();

        // For 45-degree curves, we calculate a quarter of the full curve
        switch (curveType) {
            case NORTH_TO_EAST:
                positions = calculateQuarterCircle(start, radius, 0, 45);
                break;
            case NORTH_TO_WEST:
                positions = calculateQuarterCircle(start, radius, 135, 180);
                break;
            case SOUTH_TO_EAST:
                positions = calculateQuarterCircle(start, radius, 315, 360);
                break;
            case SOUTH_TO_WEST:
                positions = calculateQuarterCircle(start, radius, 180, 225);
                break;
            case EAST_TO_NORTH:
                positions = calculateQuarterCircle(start, radius, 0, 45);
                break;
            case EAST_TO_SOUTH:
                positions = calculateQuarterCircle(start, radius, 315, 360);
                break;
            case WEST_TO_NORTH:
                positions = calculateQuarterCircle(start, radius, 135, 180);
                break;
            case WEST_TO_SOUTH:
                positions = calculateQuarterCircle(start, radius, 180, 225);
                break;
        }

        return positions;
    }

    /**
     * Calculates points along a circular arc.
     */
    private List<BlockPos> calculateQuarterCircle(BlockPos center, int radius, int startAngle, int endAngle) {
        List<BlockPos> positions = new ArrayList<>();

        // Calculate number of segments based on radius
        int segments = Math.max(radius / 2, 8); // At least 8 segments for smoothness

        double angleRange = Math.toRadians(endAngle - startAngle);
        double startRad = Math.toRadians(startAngle);

        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double angle = startRad + angleRange * t;

            int x = (int) Math.round(center.getX() + radius * Math.cos(angle));
            int z = (int) Math.round(center.getZ() + radius * Math.sin(angle));

            BlockPos pos = new BlockPos(x, center.getY(), z);

            // Avoid duplicates
            if (positions.isEmpty() || !positions.get(positions.size() - 1).equals(pos)) {
                positions.add(pos);
            }
        }

        return positions;
    }

    /**
     * Determines the curve type needed to connect two directions.
     */
    public CurveType determineCurveType(Direction from, Direction to) {
        if (from == Direction.NORTH) {
            if (to == Direction.EAST) return CurveType.NORTH_TO_EAST;
            if (to == Direction.WEST) return CurveType.NORTH_TO_WEST;
        } else if (from == Direction.SOUTH) {
            if (to == Direction.EAST) return CurveType.SOUTH_TO_EAST;
            if (to == Direction.WEST) return CurveType.SOUTH_TO_WEST;
        } else if (from == Direction.EAST) {
            if (to == Direction.NORTH) return CurveType.EAST_TO_NORTH;
            if (to == Direction.SOUTH) return CurveType.EAST_TO_SOUTH;
        } else if (from == Direction.WEST) {
            if (to == Direction.NORTH) return CurveType.WEST_TO_NORTH;
            if (to == Direction.SOUTH) return CurveType.WEST_TO_SOUTH;
        }

        // Invalid curve
        return null;
    }

    /**
     * Validates if a curve is possible between two directions.
     */
    public boolean isValidCurve(Direction from, Direction to) {
        // Can't curve from same direction
        if (from == to) return false;

        // Can't curve to opposite direction (that's a straight line)
        if (from == to.getOpposite()) return false;

        // Curves must be 90 degrees (perpendicular directions)
        return from.getAxis() != to.getAxis();
    }

    /**
     * Calculates the optimal radius for a curve based on space available.
     */
    public int calculateOptimalRadius(BlockPos start, BlockPos end, CurveType curveType) {
        // Calculate distance between points
        int dx = Math.abs(end.getX() - start.getX());
        int dz = Math.abs(end.getZ() - start.getZ());

        // For a 90-degree curve, the radius should be approximately half the distance
        int estimatedRadius = Math.max(dx, dz) / 2;

        // Clamp to configured limits
        estimatedRadius = Math.max(config.TRACK_MIN_CURVE_RADIUS, estimatedRadius);
        estimatedRadius = Math.min(estimatedRadius, config.TRACK_MIN_CURVE_RADIUS * 4); // Max 4x minimum

        return estimatedRadius;
    }

    /**
     * Creates a smooth curve path between two points.
     */
    public List<BlockPos> createCurvePath(BlockPos start, BlockPos end, Direction fromDir, Direction toDir) {
        // Validate curve
        if (!isValidCurve(fromDir, toDir)) {
            return new ArrayList<>();
        }

        // Determine curve type
        CurveType curveType = determineCurveType(fromDir, toDir);
        if (curveType == null) {
            return new ArrayList<>();
        }

        // Calculate optimal radius
        int radius = calculateOptimalRadius(start, end, curveType);

        // Use 90-degree curve by default (can be configured)
        if (config.TRACK_MAX_CURVE_ANGLE >= 90) {
            return calculate90DegreeCurve(start, curveType, radius);
        } else {
            return calculate45DegreeCurve(start, curveType, radius);
        }
    }

    /**
     * Interpolates height along a curve (for smooth elevation changes).
     */
    public int interpolateHeight(BlockPos start, BlockPos end, BlockPos current, int curveIndex, int totalCurvePoints) {
        // Linear interpolation
        double t = curveIndex / (double) (totalCurvePoints - 1);
        return (int) Math.round(start.getY() + (end.getY() - start.getY()) * t);
    }
}
