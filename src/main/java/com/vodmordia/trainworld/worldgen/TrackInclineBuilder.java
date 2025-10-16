package com.vodmordia.trainworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds inclined track sections following Create's incline rules:
 * - 45° inclines can be any height and length
 * - S-curve inclines: max elevation change of 11 blocks, max horizontal distance of 31 blocks
 * - Transitions from level to 45° must use placement assist logic
 */
public class TrackInclineBuilder {

    private final TrainWorldConfig config;

    public TrackInclineBuilder(TrainWorldConfig config) {
        this.config = config;
    }

    /**
     * Types of inclines supported by Create.
     */
    public enum InclineType {
        STRAIGHT_45,     // Simple 45-degree incline
        S_CURVE          // S-curve incline for gradual elevation change
    }

    /**
     * Represents an incline segment.
     */
    public static class InclineSegment {
        public final BlockPos position;
        public final int height;
        public final InclineType type;
        public final Direction direction;

        public InclineSegment(BlockPos position, int height, InclineType type, Direction direction) {
            this.position = position;
            this.height = height;
            this.type = type;
            this.direction = direction;
        }
    }

    /**
     * Calculates a 45-degree incline.
     */
    public List<InclineSegment> calculate45DegreeIncline(BlockPos start, BlockPos end, Direction direction) {
        List<InclineSegment> segments = new ArrayList<>();

        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();

        // Calculate horizontal distance
        int horizontalDistance = Math.max(Math.abs(dx), Math.abs(dz));

        // For 45-degree incline, we need equal horizontal and vertical distance
        // Adjust if needed
        int targetHeight = Math.abs(dy);
        int steps = Math.max(horizontalDistance, targetHeight);

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;

            int x = (int) Math.round(start.getX() + dx * t);
            int y = (int) Math.round(start.getY() + dy * t);
            int z = (int) Math.round(start.getZ() + dz * t);

            BlockPos pos = new BlockPos(x, y, z);
            segments.add(new InclineSegment(pos, y, InclineType.STRAIGHT_45, direction));
        }

        return segments;
    }

    /**
     * Calculates an S-curve incline (smooth elevation change).
     */
    public List<InclineSegment> calculateSCurveIncline(BlockPos start, BlockPos end, Direction direction) {
        List<InclineSegment> segments = new ArrayList<>();

        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();

        // Validate S-curve constraints
        int horizontalDistance = Math.max(Math.abs(dx), Math.abs(dz));
        int heightChange = Math.abs(dy);

        if (heightChange > 11) {
            // Height too large for single S-curve, use multiple segments
            return calculateStackedSCurves(start, end, direction);
        }

        if (horizontalDistance > 31) {
            // Distance too large for single S-curve, use multiple segments
            return calculateStackedSCurves(start, end, direction);
        }

        // Check minimum horizontal distance for this height
        int minDistance = getMinimumHorizontalDistanceForHeight(heightChange);
        if (horizontalDistance < minDistance) {
            // Not enough space for smooth S-curve, fall back to 45-degree
            return calculate45DegreeIncline(start, end, direction);
        }

        // Calculate S-curve using smooth interpolation
        int steps = horizontalDistance;

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;

            // S-curve interpolation (smooth start and end)
            double smoothT = smoothStep(t);

            int x = (int) Math.round(start.getX() + dx * t);
            int y = (int) Math.round(start.getY() + dy * smoothT);
            int z = (int) Math.round(start.getZ() + dz * t);

            BlockPos pos = new BlockPos(x, y, z);
            segments.add(new InclineSegment(pos, y, InclineType.S_CURVE, direction));
        }

        return segments;
    }

    /**
     * Calculates stacked S-curves for longer/taller inclines.
     */
    private List<InclineSegment> calculateStackedSCurves(BlockPos start, BlockPos end, Direction direction) {
        List<InclineSegment> segments = new ArrayList<>();

        int totalHeightChange = end.getY() - start.getY();
        int totalHorizontalDistance = Math.max(Math.abs(end.getX() - start.getX()),
                                               Math.abs(end.getZ() - start.getZ()));

        // Calculate number of S-curve segments needed
        int numSegments = Math.max(
            (int) Math.ceil(Math.abs(totalHeightChange) / 11.0),
            (int) Math.ceil(totalHorizontalDistance / 31.0)
        );

        // Divide the incline into multiple S-curves
        for (int seg = 0; seg < numSegments; seg++) {
            double segStart = seg / (double) numSegments;
            double segEnd = (seg + 1) / (double) numSegments;

            int startX = (int) Math.round(start.getX() + (end.getX() - start.getX()) * segStart);
            int startY = (int) Math.round(start.getY() + totalHeightChange * segStart);
            int startZ = (int) Math.round(start.getZ() + (end.getZ() - start.getZ()) * segStart);

            int endX = (int) Math.round(start.getX() + (end.getX() - start.getX()) * segEnd);
            int endY = (int) Math.round(start.getY() + totalHeightChange * segEnd);
            int endZ = (int) Math.round(start.getZ() + (end.getZ() - start.getZ()) * segEnd);

            BlockPos segmentStart = new BlockPos(startX, startY, startZ);
            BlockPos segmentEnd = new BlockPos(endX, endY, endZ);

            segments.addAll(calculateSCurveIncline(segmentStart, segmentEnd, direction));
        }

        return segments;
    }

    /**
     * Smooth step interpolation for S-curves.
     */
    private double smoothStep(double t) {
        // Smoothstep function: 3t² - 2t³
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Gets the minimum horizontal distance required for a given height change in an S-curve.
     * Based on Create's S-curve distance chart.
     */
    private int getMinimumHorizontalDistanceForHeight(int heightChange) {
        // Create's S-curve minimum distances (approximate)
        switch (heightChange) {
            case 0:
            case 1: return 8;
            case 2: return 10;
            case 3: return 12;
            case 4: return 14;
            case 5: return 15;
            case 6: return 16;
            case 7: return 18;
            case 8: return 20;
            case 9: return 22;
            case 10: return 24;
            case 11: return 26;
            default: return heightChange * 3; // Rough estimate for higher values
        }
    }

    /**
     * Determines if an incline is needed between two points.
     */
    public boolean needsIncline(BlockPos start, BlockPos end) {
        return start.getY() != end.getY();
    }

    /**
     * Chooses the best incline type for the given height change and distance.
     */
    public InclineType chooseBestInclineType(BlockPos start, BlockPos end) {
        int heightChange = Math.abs(end.getY() - start.getY());
        int horizontalDistance = Math.max(Math.abs(end.getX() - start.getX()),
                                         Math.abs(end.getZ() - start.getZ()));

        // If height change is 0, no incline needed
        if (heightChange == 0) {
            return null;
        }

        // If very short distance, use 45-degree
        if (horizontalDistance < 8) {
            return InclineType.STRAIGHT_45;
        }

        // If height change is small and we have space, use S-curve
        if (heightChange <= 11 && horizontalDistance <= 31) {
            int minDistance = getMinimumHorizontalDistanceForHeight(heightChange);
            if (horizontalDistance >= minDistance) {
                return InclineType.S_CURVE;
            }
        }

        // Default to 45-degree incline
        return InclineType.STRAIGHT_45;
    }

    /**
     * Builds an incline with automatic type selection.
     */
    public List<InclineSegment> buildIncline(BlockPos start, BlockPos end, Direction direction) {
        InclineType type = chooseBestInclineType(start, end);

        if (type == null) {
            return new ArrayList<>();
        }

        switch (type) {
            case STRAIGHT_45:
                return calculate45DegreeIncline(start, end, direction);
            case S_CURVE:
                return calculateSCurveIncline(start, end, direction);
            default:
                return new ArrayList<>();
        }
    }

    /**
     * Creates transition segments from level track to incline.
     */
    public List<InclineSegment> createTransition(BlockPos levelEnd, BlockPos inclineStart, Direction direction) {
        List<InclineSegment> transition = new ArrayList<>();

        // Transition should be smooth - add 2-3 blocks for gradual change
        int transitionLength = 3;

        for (int i = 0; i < transitionLength; i++) {
            double t = i / (double) (transitionLength - 1);
            double smoothT = smoothStep(t);

            int x = (int) Math.round(levelEnd.getX() + (inclineStart.getX() - levelEnd.getX()) * t);
            int y = (int) Math.round(levelEnd.getY() + (inclineStart.getY() - levelEnd.getY()) * smoothT);
            int z = (int) Math.round(levelEnd.getZ() + (inclineStart.getZ() - levelEnd.getZ()) * t);

            BlockPos pos = new BlockPos(x, y, z);
            transition.add(new InclineSegment(pos, y, InclineType.S_CURVE, direction));
        }

        return transition;
    }

    /**
     * Validates if an incline meets Create's rules.
     */
    public boolean isValidIncline(List<InclineSegment> segments, InclineType type) {
        if (segments.isEmpty()) {
            return false;
        }

        InclineSegment first = segments.get(0);
        InclineSegment last = segments.get(segments.size() - 1);

        int heightChange = Math.abs(last.height - first.height);
        int horizontalDistance = Math.max(
            Math.abs(last.position.getX() - first.position.getX()),
            Math.abs(last.position.getZ() - first.position.getZ())
        );

        if (type == InclineType.S_CURVE) {
            // Check S-curve constraints
            if (heightChange > 11) return false;
            if (horizontalDistance > 31) return false;

            int minDistance = getMinimumHorizontalDistanceForHeight(heightChange);
            if (horizontalDistance < minDistance) return false;
        }

        return true;
    }
}
