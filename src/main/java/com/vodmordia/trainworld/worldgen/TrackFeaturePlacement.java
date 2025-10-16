package com.vodmordia.trainworld.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

import java.util.List;

/**
 * Handles the actual placement of track blocks in the world.
 * Converts track network information into physical blocks.
 */
public class TrackFeaturePlacement {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final TrainWorldConfig config;
    private final TrackCurveBuilder curveBuilder;
    private final TrackInclineBuilder inclineBuilder;
    private final TrackBridgeBuilder bridgeBuilder;

    public TrackFeaturePlacement(TrainWorldConfig config) {
        this.config = config;
        this.curveBuilder = new TrackCurveBuilder(config);
        this.inclineBuilder = new TrackInclineBuilder(config);
        this.bridgeBuilder = new TrackBridgeBuilder(config);
    }

    /**
     * Places tracks for a chunk based on its network type.
     */
    public void placeTracksInChunk(WorldGenLevel level, ChunkAccess chunk, TrackNetworkChunk trackInfo, int trackHeight) {
        if (trackInfo.isEmpty()) {
            return;
        }

        BlockPos chunkStart = chunk.getPos().getWorldPosition();

        switch (trackInfo.getType()) {
            case STRAIGHT_NS:
                placeStraightTrack(level, chunkStart, trackHeight, Direction.NORTH, trackInfo.getTrackLanes());
                break;

            case STRAIGHT_EW:
                placeStraightTrack(level, chunkStart, trackHeight, Direction.EAST, trackInfo.getTrackLanes());
                break;

            case CURVE_NE:
                placeCurveTrack(level, chunkStart, trackHeight, Direction.NORTH, Direction.EAST);
                break;

            case CURVE_NW:
                placeCurveTrack(level, chunkStart, trackHeight, Direction.NORTH, Direction.WEST);
                break;

            case CURVE_SE:
                placeCurveTrack(level, chunkStart, trackHeight, Direction.SOUTH, Direction.EAST);
                break;

            case CURVE_SW:
                placeCurveTrack(level, chunkStart, trackHeight, Direction.SOUTH, Direction.WEST);
                break;

            case JUNCTION_4WAY:
                placeJunction(level, chunkStart, trackHeight, 4);
                break;

            case JUNCTION_3WAY_N:
            case JUNCTION_3WAY_S:
            case JUNCTION_3WAY_E:
            case JUNCTION_3WAY_W:
                placeJunction(level, chunkStart, trackHeight, 3);
                break;

            case STATION:
                placeStation(level, chunkStart, trackHeight, trackInfo);
                break;

            case BRIDGE_NS:
                placeBridge(level, chunkStart, trackHeight, Direction.NORTH);
                break;

            case BRIDGE_EW:
                placeBridge(level, chunkStart, trackHeight, Direction.EAST);
                break;

            case TUNNEL_NS:
                placeTunnel(level, chunkStart, trackHeight, Direction.NORTH);
                break;

            case TUNNEL_EW:
                placeTunnel(level, chunkStart, trackHeight, Direction.EAST);
                break;

            case INCLINE_UP_N:
            case INCLINE_DOWN_N:
                placeIncline(level, chunkStart, trackHeight, Direction.NORTH, trackInfo.getType().name().contains("UP"));
                break;

            case INCLINE_UP_S:
            case INCLINE_DOWN_S:
                placeIncline(level, chunkStart, trackHeight, Direction.SOUTH, trackInfo.getType().name().contains("UP"));
                break;

            case INCLINE_UP_E:
            case INCLINE_DOWN_E:
                placeIncline(level, chunkStart, trackHeight, Direction.EAST, trackInfo.getType().name().contains("UP"));
                break;

            case INCLINE_UP_W:
            case INCLINE_DOWN_W:
                placeIncline(level, chunkStart, trackHeight, Direction.WEST, trackInfo.getType().name().contains("UP"));
                break;
        }
    }

    /**
     * Places a straight track through the chunk.
     */
    private void placeStraightTrack(WorldGenLevel level, BlockPos chunkStart, int trackHeight, Direction direction, int lanes) {
        BlockState trackBlock = getTrackBlockForDirection(direction);

        // Calculate track positions based on direction
        for (int lane = 0; lane < lanes; lane++) {
            int laneOffset = (lanes > 1) ? (lane - lanes / 2) * 2 : 0;

            for (int i = 0; i < 16; i++) {
                BlockPos trackPos;

                if (direction == Direction.NORTH || direction == Direction.SOUTH) {
                    // North-South track
                    trackPos = chunkStart.offset(8 + laneOffset, trackHeight, i);
                } else {
                    // East-West track
                    trackPos = chunkStart.offset(i, trackHeight, 8 + laneOffset);
                }

                placeTrackBlock(level, trackPos, trackBlock, direction);
            }
        }
    }

    /**
     * Places a curved track section.
     */
    private void placeCurveTrack(WorldGenLevel level, BlockPos chunkStart, int trackHeight, Direction from, Direction to) {
        // Calculate curve center
        BlockPos curveCenter = chunkStart.offset(8, trackHeight, 8);

        // Generate curve path
        TrackCurveBuilder.CurveType curveType = curveBuilder.determineCurveType(from, to);
        if (curveType == null) {
            return;
        }

        List<BlockPos> curvePath = curveBuilder.calculate90DegreeCurve(curveCenter, curveType, config.TRACK_MIN_CURVE_RADIUS);

        // Place track blocks along curve
        for (BlockPos pos : curvePath) {
            if (isWithinChunk(pos, chunkStart)) {
                BlockState trackBlock = getTrackBlockForCurve(from, to);
                placeTrackBlock(level, pos, trackBlock, from); // Use 'from' direction for clearing
            }
        }
    }

    /**
     * Places a junction (intersection).
     */
    private void placeJunction(WorldGenLevel level, BlockPos chunkStart, int trackHeight, int numConnections) {
        BlockPos center = chunkStart.offset(8, trackHeight, 8);

        // Place junction block at center
        BlockState junctionBlock = getJunctionBlock(numConnections);
        placeTrackBlock(level, center, junctionBlock, null); // No specific direction for junction center

        // Place connecting tracks in all directions
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState trackBlock = getTrackBlockForDirection(dir);
            for (int i = 1; i <= 7; i++) {
                BlockPos trackPos = center.relative(dir, i);
                if (isWithinChunk(trackPos, chunkStart)) {
                    placeTrackBlock(level, trackPos, trackBlock, dir);
                }
            }
        }
    }

    /**
     * Places a station platform.
     */
    private void placeStation(WorldGenLevel level, BlockPos chunkStart, int trackHeight, TrackNetworkChunk trackInfo) {
        // Place platform blocks
        for (int x = 4; x < 12; x++) {
            for (int z = 4; z < 12; z++) {
                BlockPos platformPos = chunkStart.offset(x, trackHeight - 1, z);
                level.setBlock(platformPos, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
        }

        // Place tracks through the station
        placeStraightTrack(level, chunkStart, trackHeight, Direction.NORTH, trackInfo.getTrackLanes());
        placeStraightTrack(level, chunkStart, trackHeight, Direction.EAST, trackInfo.getTrackLanes());
    }

    /**
     * Places a bridge section.
     */
    private void placeBridge(WorldGenLevel level, BlockPos chunkStart, int trackHeight, Direction direction) {
        BlockPos bridgeStart = chunkStart.offset(0, trackHeight, 0);
        BlockPos bridgeEnd = chunkStart.offset(15, trackHeight, 15);

        // Generate bridge structure
        bridgeBuilder.generateBridge(level, bridgeStart, bridgeEnd, trackHeight, direction);

        // Place tracks on top
        placeStraightTrack(level, chunkStart, trackHeight, direction, 1);
    }

    /**
     * Places a tunnel section.
     */
    private void placeTunnel(WorldGenLevel level, BlockPos chunkStart, int trackHeight, Direction direction) {
        // Clear tunnel space (3x3 cross-section)
        for (int i = 0; i < 16; i++) {
            for (int y = trackHeight; y < trackHeight + 3; y++) {
                for (int offset = -1; offset <= 1; offset++) {
                    BlockPos clearPos;

                    if (direction == Direction.NORTH || direction == Direction.SOUTH) {
                        clearPos = chunkStart.offset(8 + offset, y, i);
                    } else {
                        clearPos = chunkStart.offset(i, y, 8 + offset);
                    }

                    // Replace solid blocks with air
                    if (level.getBlockState(clearPos).isSolid()) {
                        level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        // Place tracks
        placeStraightTrack(level, chunkStart, trackHeight, direction, 1);

        // Place tunnel walls/ceiling (using stone bricks)
        // TODO: Implement proper tunnel walls
    }

    /**
     * Places an incline section.
     */
    private void placeIncline(WorldGenLevel level, BlockPos chunkStart, int trackHeight, Direction direction, boolean goingUp) {
        BlockPos inclineStart = chunkStart.offset(8, trackHeight, goingUp ? 0 : 15);
        BlockPos inclineEnd = chunkStart.offset(8, trackHeight + (goingUp ? 5 : -5), goingUp ? 15 : 0);

        // Build incline
        List<TrackInclineBuilder.InclineSegment> segments = inclineBuilder.buildIncline(inclineStart, inclineEnd, direction);

        // Place track blocks along incline
        for (TrackInclineBuilder.InclineSegment segment : segments) {
            if (isWithinChunk(segment.position, chunkStart)) {
                BlockState trackBlock = getTrackBlockForIncline(direction, goingUp);
                placeTrackBlock(level, segment.position, trackBlock, direction);
            }
        }
    }

    /**
     * Places a single track block at the specified position.
     */
    private void placeTrackBlock(WorldGenLevel level, BlockPos pos, BlockState trackBlock, Direction trackDirection) {
        BlockState existing = level.getBlockState(pos);

        // If there's a solid block in the way, try to clear it (unless it's bedrock or important structure)
        if (!existing.isAir() && !existing.canBeReplaced()) {
            // Don't replace bedrock, end portal frames, or other important blocks
            if (existing.is(Blocks.BEDROCK) ||
                existing.is(Blocks.END_PORTAL_FRAME) ||
                existing.is(Blocks.COMMAND_BLOCK) ||
                existing.is(Blocks.BARRIER)) {
                if (config.DEBUG_MODE) {
                    System.out.println("[TrainWorld] Cannot place track at " + pos + " - protected block: " + existing.getBlock().getName().getString());
                }
                return;
            }

            // For other solid blocks (trees, stones, etc.), replace them
            // This allows tracks to cut through terrain
            if (config.DEBUG_MODE) {
                System.out.println("[TrainWorld] Replacing " + existing.getBlock().getName().getString() + " at " + pos + " with track");
            }
        }

        // Clear space around and above the track
        if (trackDirection != null) {
            LOGGER.info("[TrainWorld] Calling clearTrackArea for track at {} with direction {}", pos, trackDirection);
            clearTrackArea(level, pos, trackDirection);
        } else {
            LOGGER.warn("[TrainWorld] trackDirection is NULL for track at {}, no clearing will happen!", pos);
        }

        // Build pillar down to ground if needed
        buildSupportPillar(level, pos);

        // Place the track with flag 2 (update neighbors, no block updates during worldgen)
        // This prevents the "failed to load block entity" warnings
        level.setBlock(pos, trackBlock, 2);
    }

    /**
     * Clears space around and above a track position.
     * Create tracks are 2 blocks wide, so we need to clear:
     * - The track block itself
     * - 1 additional block perpendicular to travel direction (for the 2-block width)
     * - TRACK_OUTSIDE_SPACE blocks to the left/right at track level
     * - TRACK_TUNNEL_HEIGHT blocks above the track (including the outside space width)
     */
    private void clearTrackArea(WorldGenLevel level, BlockPos trackPos, Direction trackDirection) {
        if (config.DEBUG_MODE) {
            System.out.println("[TrainWorld] clearTrackArea called at " + trackPos + " direction=" + trackDirection + " OUTSIDE_SPACE=" + config.TRACK_OUTSIDE_SPACE);
        }

        // Determine perpendicular axis (left/right relative to track direction)
        boolean clearAlongX = (trackDirection == Direction.NORTH || trackDirection == Direction.SOUTH);

        // Create tracks are 2 blocks wide perpendicular to travel direction
        // Clear both blocks of the track width plus the outside space on each side
        int totalClearWidth = config.TRACK_OUTSIDE_SPACE + 1; // +1 for the second block of track width

        // Clear at track level (both track blocks + outside space)
        for (int offset = -totalClearWidth; offset <= totalClearWidth; offset++) {
            BlockPos clearPos = clearAlongX ?
                trackPos.offset(offset, 0, 0) :
                trackPos.offset(0, 0, offset);

            clearBlockIfNeeded(level, clearPos);
        }

        // Clear above the track (tunnel height)
        for (int y = 1; y <= config.TRACK_TUNNEL_HEIGHT; y++) {
            for (int offset = -totalClearWidth; offset <= totalClearWidth; offset++) {
                BlockPos clearPos = clearAlongX ?
                    trackPos.offset(offset, y, 0) :
                    trackPos.offset(0, y, offset);

                clearBlockIfNeeded(level, clearPos);
            }
        }
    }

    /**
     * Clears a block if it's not protected (bedrock, etc.).
     * Clears solid blocks, plants, grass, leaves, and other ground cover.
     */
    private void clearBlockIfNeeded(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // Don't clear air or already clear blocks
        if (state.isAir()) {
            return;
        }

        // Don't clear protected blocks
        if (state.is(Blocks.BEDROCK) ||
            state.is(Blocks.END_PORTAL_FRAME) ||
            state.is(Blocks.COMMAND_BLOCK) ||
            state.is(Blocks.BARRIER)) {
            return;
        }

        // Check if this is a block that should be cleared
        boolean shouldClear = false;

        // Clear all replaceable blocks (grass, flowers, etc.)
        if (state.canBeReplaced()) {
            shouldClear = true;
            if (config.DEBUG_MODE) {
                System.out.println("[TrainWorld] Clearing replaceable block at " + pos + ": " + state.getBlock().getName().getString());
            }
        }

        // Clear leaves explicitly (they might not be replaceable)
        if (state.is(Blocks.OAK_LEAVES) || state.is(Blocks.SPRUCE_LEAVES) ||
            state.is(Blocks.BIRCH_LEAVES) || state.is(Blocks.JUNGLE_LEAVES) ||
            state.is(Blocks.ACACIA_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES) ||
            state.is(Blocks.MANGROVE_LEAVES) || state.is(Blocks.CHERRY_LEAVES) ||
            state.is(Blocks.AZALEA_LEAVES) || state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
            shouldClear = true;
        }

        // Clear grass and tall grass (SHORT_GRASS in 1.21+)
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) ||
            state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) {
            shouldClear = true;
            if (config.DEBUG_MODE) {
                System.out.println("[TrainWorld] Clearing grass/fern at " + pos + ": " + state.getBlock().getName().getString());
            }
        }

        // Clear flowers and other plants
        if (state.is(Blocks.DANDELION) || state.is(Blocks.POPPY) ||
            state.is(Blocks.BLUE_ORCHID) || state.is(Blocks.ALLIUM) ||
            state.is(Blocks.AZURE_BLUET) || state.is(Blocks.RED_TULIP) ||
            state.is(Blocks.ORANGE_TULIP) || state.is(Blocks.WHITE_TULIP) ||
            state.is(Blocks.PINK_TULIP) || state.is(Blocks.OXEYE_DAISY) ||
            state.is(Blocks.CORNFLOWER) || state.is(Blocks.LILY_OF_THE_VALLEY) ||
            state.is(Blocks.SUNFLOWER) || state.is(Blocks.LILAC) ||
            state.is(Blocks.ROSE_BUSH) || state.is(Blocks.PEONY)) {
            shouldClear = true;
        }

        // Clear vines and hanging roots
        if (state.is(Blocks.VINE) || state.is(Blocks.WEEPING_VINES) ||
            state.is(Blocks.TWISTING_VINES) || state.is(Blocks.HANGING_ROOTS) ||
            state.is(Blocks.GLOW_LICHEN)) {
            shouldClear = true;
        }

        // Clear mushrooms and fungi
        if (state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) ||
            state.is(Blocks.CRIMSON_FUNGUS) || state.is(Blocks.WARPED_FUNGUS)) {
            shouldClear = true;
        }

        // Clear saplings
        if (state.is(Blocks.OAK_SAPLING) || state.is(Blocks.SPRUCE_SAPLING) ||
            state.is(Blocks.BIRCH_SAPLING) || state.is(Blocks.JUNGLE_SAPLING) ||
            state.is(Blocks.ACACIA_SAPLING) || state.is(Blocks.DARK_OAK_SAPLING) ||
            state.is(Blocks.MANGROVE_PROPAGULE) || state.is(Blocks.CHERRY_SAPLING)) {
            shouldClear = true;
        }

        // Clear crops and plants
        if (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) ||
            state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS) ||
            state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.SUGAR_CANE)) {
            shouldClear = true;
        }

        // Clear dead bushes and other vegetation
        if (state.is(Blocks.DEAD_BUSH) || state.is(Blocks.SEAGRASS) ||
            state.is(Blocks.TALL_SEAGRASS) || state.is(Blocks.SEA_PICKLE) ||
            state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)) {
            shouldClear = true;
        }

        // Clear snow layers
        if (state.is(Blocks.SNOW)) {
            shouldClear = true;
        }

        // If none of the above specific blocks, check if it's a fluid (which we DON'T clear)
        if (!shouldClear && !state.getFluidState().isEmpty()) {
            // Don't clear fluids (water, lava) - let them be handled separately
            return;
        }

        // If not a fluid and not already marked for clearing, clear ALL solid blocks
        // This includes sand, dirt, gravel, stone, etc.
        if (!shouldClear) {
            shouldClear = true; // Clear everything else that's not air, not fluid, and not protected
        }

        if (shouldClear) {
            LOGGER.debug("[TrainWorld] Clearing block at {}: {}", pos, state.getBlock().getName().getString());
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /**
     * Builds a support pillar from the track position down to solid ground.
     * Only builds if there's air or water below the track.
     */
    private void buildSupportPillar(WorldGenLevel level, BlockPos trackPos) {
        BlockPos checkPos = trackPos.below();
        BlockState belowState = level.getBlockState(checkPos);

        // Only build pillar if there's nothing solid directly below
        if (belowState.isSolid() && belowState.getFluidState().isEmpty()) {
            return; // Already has solid ground support
        }

        BlockState pillarBlock = CreateTrackHelper.getBridgeDeckBlock();
        int minHeight = level.getMinBuildHeight();

        // Keep going down until we hit solid ground or reach minimum build height
        while (checkPos.getY() >= minHeight) {
            belowState = level.getBlockState(checkPos);

            // If we hit solid ground (not air, not water, not lava, not replaceable), stop
            if (!belowState.isAir() &&
                belowState.getFluidState().isEmpty() &&
                !belowState.canBeReplaced() &&
                belowState.isSolid()) {
                break;
            }

            // Place pillar block (flag 2 for worldgen)
            level.setBlock(checkPos, pillarBlock, 2);
            checkPos = checkPos.below();
        }
    }

    /**
     * Checks if a position is within the chunk bounds.
     */
    private boolean isWithinChunk(BlockPos pos, BlockPos chunkStart) {
        int relX = pos.getX() - chunkStart.getX();
        int relZ = pos.getZ() - chunkStart.getZ();
        return relX >= 0 && relX < 16 && relZ >= 0 && relZ < 16;
    }

    /**
     * Gets the appropriate track block for a direction.
     */
    private BlockState getTrackBlockForDirection(Direction direction) {
        return CreateTrackHelper.getStraightTrack(direction);
    }

    /**
     * Gets the appropriate track block for a curve.
     */
    private BlockState getTrackBlockForCurve(Direction from, Direction to) {
        // For curves, we use diagonal tracks or just straight tracks
        // Create handles curves through BezierConnection, but for simple placement we use straight
        return CreateTrackHelper.getStraightTrack(from);
    }

    /**
     * Gets the appropriate junction block.
     */
    private BlockState getJunctionBlock(int numConnections) {
        // Use orthogonal crossing for junctions
        return CreateTrackHelper.getCrossingTrack(true);
    }

    /**
     * Gets the appropriate track block for an incline.
     */
    private BlockState getTrackBlockForIncline(Direction direction, boolean goingUp) {
        return CreateTrackHelper.getAscendingTrack(direction);
    }
}
