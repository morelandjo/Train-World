package com.vodmordia.trainworld.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;

/**
 * Helper class to create track connections using Create's native track placement system.
 * This hooks into Create's existing bezier creation logic instead of manually calculating everything.
 */
public class CreateTrackConnector {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Creates a track connection between two track positions using Create's BezierConnection directly.
     * This bypasses the player-dependent tryConnect() method and creates connections programmatically.
     *
     * @param level The server level
     * @param firstPos Position of the first track
     * @param secondPos Position of the second track
     * @param direction Direction of travel from first to second
     * @return true if connection was successfully created
     */
    public static boolean connectTracks(ServerLevel level, BlockPos firstPos, BlockPos secondPos, Direction direction) {
        LOGGER.info("[TrainWorld] Attempting to connect tracks: {} -> {} (direction: {})", firstPos, secondPos, direction);

        try {
            // Get block states
            BlockState firstState = level.getBlockState(firstPos);
            BlockState secondState = level.getBlockState(secondPos);

            // Get Create's ITrackBlock interface
            Class<?> iTrackBlockClass = Class.forName("com.simibubi.create.content.trains.track.ITrackBlock");

            if (!iTrackBlockClass.isInstance(firstState.getBlock()) || !iTrackBlockClass.isInstance(secondState.getBlock())) {
                LOGGER.warn("[TrainWorld] One or both blocks are not track blocks");
                return false;
            }

            // Calculate "look vector" pointing from first track to second track
            // This simulates the player looking in the direction of travel
            Vec3 lookVec = Vec3.atLowerCornerOf(secondPos.subtract(firstPos)).normalize();
            LOGGER.info("[TrainWorld] Look vector (direction of travel): {}", lookVec);

            // Get track geometry using Create's getNearestTrackAxis (same as tryConnect does)
            Object firstTrack = firstState.getBlock();
            Object secondTrack = secondState.getBlock();

            // Get the axis that best matches our look direction for first track
            Object firstAxisPair = getNearestTrackAxis(level, firstPos, firstState, firstTrack, iTrackBlockClass, lookVec);
            Vec3 firstAxis = getAxisFromPair(firstAxisPair);
            Vec3 firstNormal = getTrackNormal(level, firstPos, firstState, firstTrack, iTrackBlockClass);
            Vec3 firstEnd = getTrackCurveStart(level, firstPos, firstState, firstTrack, iTrackBlockClass, firstAxis);

            // For second track, use opposite look vector
            Object secondAxisPair = getNearestTrackAxis(level, secondPos, secondState, secondTrack, iTrackBlockClass, lookVec.scale(-1));
            Vec3 secondAxis = getAxisFromPair(secondAxisPair);
            Vec3 secondNormal = getTrackNormal(level, secondPos, secondState, secondTrack, iTrackBlockClass);
            Vec3 secondEnd = getTrackCurveStart(level, secondPos, secondState, secondTrack, iTrackBlockClass, secondAxis);

            LOGGER.info("[TrainWorld] First track: axis={}, normal={}, end={}", firstAxis, firstNormal, firstEnd);
            LOGGER.info("[TrainWorld] Second track: axis={}, normal={}, end={}", secondAxis, secondNormal, secondEnd);

            // Apply Create's axis flipping logic (from TrackPlacement.tryConnect lines 182-190)
            Vec3 normedAxis1 = firstAxis.normalize();
            Vec3 normedAxis2 = secondAxis.normalize();

            // Flip first axis if it points away from second track
            if (firstAxis.dot(secondEnd.subtract(firstEnd)) < 0) {
                firstAxis = firstAxis.scale(-1);
                normedAxis1 = normedAxis1.scale(-1);
                firstEnd = getTrackCurveStart(level, firstPos, firstState, firstTrack, iTrackBlockClass, firstAxis);
                LOGGER.info("[TrainWorld] Flipped first axis, new end: {}", firstEnd);
            }

            // Flip second axis if needed (parallel check or intersection check)
            double[] intersect = intersectAxes(firstEnd, secondEnd, normedAxis1, normedAxis2);
            boolean parallel = intersect == null;

            if ((parallel && normedAxis1.dot(normedAxis2) > 0) || (!parallel && (intersect[0] < 0 || intersect[1] < 0))) {
                secondAxis = secondAxis.scale(-1);
                normedAxis2 = normedAxis2.scale(-1);
                secondEnd = getTrackCurveStart(level, secondPos, secondState, secondTrack, iTrackBlockClass, secondAxis);
                LOGGER.info("[TrainWorld] Flipped second axis, new end: {}", secondEnd);
            }

            // Create the BezierConnection directly
            Object curve = createBezierConnection(firstPos, secondPos, firstEnd, secondEnd,
                                                  firstAxis, secondAxis, firstNormal, secondNormal);

            if (curve == null) {
                LOGGER.warn("[TrainWorld] Failed to create BezierConnection");
                return false;
            }

            // Add the connection to both track block entities
            addConnectionToBlockEntities(level, firstPos, secondPos, curve);

            LOGGER.info("[TrainWorld] Successfully created track connection!");
            return true;

        } catch (Exception e) {
            LOGGER.error("[TrainWorld] Failed to create track connection: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Creates a BezierConnection directly without using TrackPlacement.tryConnect()
     */
    private static Object createBezierConnection(BlockPos pos1, BlockPos pos2,
                                                  Vec3 end1, Vec3 end2,
                                                  Vec3 axis1, Vec3 axis2,
                                                  Vec3 normal1, Vec3 normal2) throws Exception {
        // Get Create classes
        Class<?> bezierConnectionClass = Class.forName("com.simibubi.create.content.trains.track.BezierConnection");
        Class<?> coupleClass = Class.forName("net.createmod.catnip.data.Couple");
        Class<?> trackMaterialClass = Class.forName("com.simibubi.create.content.trains.track.TrackMaterial");

        // Get ANDESITE track material
        Object andesiteMaterial = trackMaterialClass.getField("ANDESITE").get(null);

        // Create Couple instances for the bezier
        var coupleCreateMethod = coupleClass.getMethod("create", Object.class, Object.class);
        Object posCouple = coupleCreateMethod.invoke(null, pos1, pos2);
        Object teaserCouple = coupleCreateMethod.invoke(null, end1, end2);
        Object axesCouple = coupleCreateMethod.invoke(null, axis1.normalize(), axis2.normalize());
        Object normalsCouple = coupleCreateMethod.invoke(null, normal1, normal2);

        LOGGER.info("[TrainWorld] Creating BezierConnection with:");
        LOGGER.info("  pos: {} -> {}", pos1, pos2);
        LOGGER.info("  teaser: {} -> {}", end1, end2);
        LOGGER.info("  axes: {} -> {}", axis1.normalize(), axis2.normalize());
        LOGGER.info("  normals: {} -> {}", normal1, normal2);

        // Create the BezierConnection
        var constructor = bezierConnectionClass.getConstructor(
            coupleClass, coupleClass, coupleClass, coupleClass,
            boolean.class, boolean.class, trackMaterialClass
        );

        Object connection = constructor.newInstance(
            posCouple, teaserCouple, axesCouple, normalsCouple,
            true,           // primary
            false,          // no girder
            andesiteMaterial
        );

        LOGGER.info("[TrainWorld] Created BezierConnection successfully");
        return connection;
    }

    /**
     * Gets the nearest track axis using Create's ITrackBlock.getNearestTrackAxis method
     */
    private static Object getNearestTrackAxis(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                               Object trackBlock, Class<?> iTrackBlockClass, Vec3 lookVec) throws Exception {
        return iTrackBlockClass.getMethod("getNearestTrackAxis",
                net.minecraft.world.level.BlockGetter.class,
                BlockPos.class,
                net.minecraft.world.level.block.state.BlockState.class,
                Vec3.class
        ).invoke(trackBlock, level, pos, state, lookVec);
    }

    /**
     * Extracts the Vec3 axis from a Pair<Vec3, AxisDirection>
     */
    private static Vec3 getAxisFromPair(Object pair) throws Exception {
        // Pair.getFirst() returns the Vec3
        Vec3 axis = (Vec3) pair.getClass().getMethod("getFirst").invoke(pair);

        // Get the AxisDirection (second element)
        Object axisDirection = pair.getClass().getMethod("getSecond").invoke(pair);

        // Check if it's POSITIVE or NEGATIVE and scale accordingly
        // AxisDirection.POSITIVE means scale by -1 (from Create's tryConnect line 147-148)
        String dirName = axisDirection.toString();
        if (dirName.equals("POSITIVE")) {
            axis = axis.scale(-1);
        }

        return axis;
    }

    /**
     * Intersects two lines in 2D (Y axis), returns [t, u] or null if parallel
     * This is a simplified version of VecHelper.intersect from Create
     */
    private static double[] intersectAxes(Vec3 start1, Vec3 start2, Vec3 dir1, Vec3 dir2) {
        // Project to XZ plane for intersection test
        double x1 = start1.x;
        double z1 = start1.z;
        double dx1 = dir1.x;
        double dz1 = dir1.z;

        double x2 = start2.x;
        double z2 = start2.z;
        double dx2 = dir2.x;
        double dz2 = dir2.z;

        double det = dx1 * dz2 - dz1 * dx2;

        // Parallel check
        if (Math.abs(det) < 1e-6) {
            return null;
        }

        double t = ((x2 - x1) * dz2 - (z2 - z1) * dx2) / det;
        double u = ((x2 - x1) * dz1 - (z2 - z1) * dx1) / det;

        return new double[]{t, u};
    }

    /**
     * Gets the track normal (up vector) from a track block
     */
    private static Vec3 getTrackNormal(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, Object trackBlock, Class<?> iTrackBlockClass) throws Exception {
        return (Vec3) iTrackBlockClass.getMethod("getUpNormal",
                net.minecraft.world.level.BlockGetter.class,
                BlockPos.class,
                BlockState.class
        ).invoke(trackBlock, level, pos, state);
    }

    /**
     * Gets the curve start position from a track block
     */
    private static Vec3 getTrackCurveStart(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, Object trackBlock, Class<?> iTrackBlockClass, Vec3 axis) throws Exception {
        return (Vec3) iTrackBlockClass.getMethod("getCurveStart",
                net.minecraft.world.level.BlockGetter.class,
                BlockPos.class,
                BlockState.class,
                Vec3.class
        ).invoke(trackBlock, level, pos, state, axis);
    }

    /**
     * Adds the bezier connection to both track block entities
     */
    private static void addConnectionToBlockEntities(ServerLevel level, BlockPos firstPos, BlockPos secondPos, Object curve) throws Exception {
        var firstBE = level.getBlockEntity(firstPos);
        var secondBE = level.getBlockEntity(secondPos);

        Class<?> trackBEClass = Class.forName("com.simibubi.create.content.trains.track.TrackBlockEntity");

        if (trackBEClass.isInstance(firstBE) && trackBEClass.isInstance(secondBE)) {
            // Add primary connection to first track
            trackBEClass.getMethod("addConnection", curve.getClass()).invoke(firstBE, curve);

            // Get secondary connection for second track
            Object secondaryCurve = curve.getClass().getMethod("secondary").invoke(curve);
            trackBEClass.getMethod("addConnection", curve.getClass()).invoke(secondBE, secondaryCurve);

            // Apply tilt smoothing
            Object firstTilt = trackBEClass.getField("tilt").get(firstBE);
            firstTilt.getClass().getMethod("tryApplySmoothing").invoke(firstTilt);

            Object secondTilt = trackBEClass.getField("tilt").get(secondBE);
            secondTilt.getClass().getMethod("tryApplySmoothing").invoke(secondTilt);

            LOGGER.info("[TrainWorld] Added connections to both track block entities");
        } else {
            LOGGER.warn("[TrainWorld] Block entities are not TrackBlockEntity instances");
        }
    }
}
