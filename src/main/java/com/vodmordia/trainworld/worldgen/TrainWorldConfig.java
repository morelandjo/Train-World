package com.vodmordia.trainworld.worldgen;

/**
 * Configuration for Train World generation.
 * Controls track network density, station placement, terrain integration, and more.
 */
public class TrainWorldConfig {

    // ===== TRACK NETWORK SPACING =====

    /** Grid spacing for track network. Stations appear at grid intersections. (Power of 2 minus 1 for mask) */
    public int TRACK_SPACING_MASK = 15; // Every 16 chunks

    /** Overall track network density (0.0 = sparse, 1.0 = dense) */
    public float TRACK_NETWORK_DENSITY = 0.5f;

    /** Whether tracks only generate between stations/cities */
    public boolean TRACKS_BETWEEN_STATIONS_ONLY = true;

    /** Minimum distance in chunks between track connection points */
    public int TRACK_MIN_HOP_DISTANCE = 5;

    // ===== PERLIN NOISE CONFIGURATION =====

    /** Main Perlin noise scale for track network distribution */
    public float TRACK_PERLIN_SCALE = 25.0f;

    /** Secondary Perlin noise scale for local variations */
    public float TRACK_SECONDARY_PERLIN_SCALE = 10.0f;

    /** Perlin noise offset for variety */
    public float TRACK_PERLIN_OFFSET = 0.0f;

    /** Perlin threshold factor - higher means less tracks */
    public float TRACK_PERLIN_FACTOR = 1.0f;

    // ===== TRACK TYPES AND HEIGHTS =====

    /** Height offset for surface tracks (relative to terrain) */
    public int TRACK_SURFACE_HEIGHT_OFFSET = 0;

    /** Depth below surface for tunnel tracks */
    public int TRACK_TUNNEL_DEPTH = 10;

    /** Whether to generate bridges over gaps */
    public boolean TRACK_USE_BRIDGES = true;

    /** Minimum gap height to trigger bridge generation */
    public int TRACK_BRIDGE_HEIGHT_THRESHOLD = 5;

    /** Whether to use scaffolding supports for bridges and elevated tracks */
    public boolean TRACK_USE_SCAFFOLDING_SUPPORTS = true;

    /** Spacing between scaffolding pillars (in blocks) */
    public int SCAFFOLDING_SPACING = 8;

    // ===== STATION CONFIGURATION =====

    /** Distance in chunks between major stations */
    public int STATION_SPACING = 32;

    /** Chance (0.0-1.0) that a valid station location actually generates a station */
    public float STATION_GENERATION_CHANCE = 0.8f;

    /** Minimum number of connected routes required for a station */
    public int STATION_MIN_CONNECTIONS = 2;

    /** Whether to generate underground stations when surface is unavailable */
    public boolean ALLOW_UNDERGROUND_STATIONS = true;

    // ===== TRACK ROUTING =====

    /** Number of parallel track lanes at major routes */
    public int TRACK_LANES = 1;

    /** Chance (0.0-1.0) of generating branch routes */
    public float TRACK_BRANCH_CHANCE = 0.3f;

    /** Whether to generate circular/loop routes */
    public boolean TRACK_LOOP_GENERATION = false;

    /** Maximum angle change for curves (in degrees, 45 or 90 per Create rules) */
    public int TRACK_MAX_CURVE_ANGLE = 90;

    /** Minimum radius for track curves (in blocks, minimum 8 per Create rules) */
    public int TRACK_MIN_CURVE_RADIUS = 8;

    // ===== TERRAIN INTEGRATION =====

    /** Maximum slope for surface tracks before switching to tunnel */
    public float MAX_SURFACE_SLOPE = 0.6f;

    /** Whether tracks should follow terrain contours closely */
    public boolean FOLLOW_TERRAIN_CLOSELY = true;

    /** Smoothing factor for height transitions between chunks (0.0-1.0) */
    public float HEIGHT_SMOOTHING_FACTOR = 0.7f;

    /** Minimum terrain height for track placement */
    public int MIN_TERRAIN_HEIGHT = 60;

    /** Maximum terrain height for track placement */
    public int MAX_TERRAIN_HEIGHT = 90;

    // ===== TRACK CLEARANCE =====

    /** Number of blocks to clear to the left and right of tracks at track level */
    public int TRACK_OUTSIDE_SPACE = 4;

    /** Height above tracks to clear (creates tunnel/clearance space) */
    public int TRACK_TUNNEL_HEIGHT = 8;

    // ===== TRACK MATERIALS =====

    /** Default track material (from Create mod) */
    public String DEFAULT_TRACK_MATERIAL = "andesite";

    /** Whether to use different materials based on biome */
    public boolean BIOME_SPECIFIC_MATERIALS = false;

    // ===== GENERATION CONTROL =====

    /** Whether track generation is enabled at all */
    public boolean TRACKS_ENABLED = true;

    /** Whether station generation is enabled */
    public boolean STATIONS_ENABLED = true;

    /** Whether to generate tracks in all biomes or just specific ones */
    public boolean GENERATE_IN_ALL_BIOMES = true;

    /** Random seed offset for track generation (added to world seed) */
    public long TRACK_SEED_OFFSET = 0xDEADBEEF;

    // ===== DEBUG OPTIONS =====

    /** Enable debug logging */
    public boolean DEBUG_MODE = true;

    /** Visualize track network with markers */
    public boolean VISUALIZE_NETWORK = false;

    /**
     * Default configuration instance.
     */
    public static final TrainWorldConfig DEFAULT = new TrainWorldConfig();

    /**
     * Creates a new configuration with default values.
     */
    public TrainWorldConfig() {
        // All fields initialized with default values above
    }

    /**
     * Creates a copy of this configuration.
     */
    public TrainWorldConfig copy() {
        TrainWorldConfig copy = new TrainWorldConfig();

        // Copy all fields
        copy.TRACK_SPACING_MASK = this.TRACK_SPACING_MASK;
        copy.TRACK_NETWORK_DENSITY = this.TRACK_NETWORK_DENSITY;
        copy.TRACKS_BETWEEN_STATIONS_ONLY = this.TRACKS_BETWEEN_STATIONS_ONLY;
        copy.TRACK_MIN_HOP_DISTANCE = this.TRACK_MIN_HOP_DISTANCE;

        copy.TRACK_PERLIN_SCALE = this.TRACK_PERLIN_SCALE;
        copy.TRACK_SECONDARY_PERLIN_SCALE = this.TRACK_SECONDARY_PERLIN_SCALE;
        copy.TRACK_PERLIN_OFFSET = this.TRACK_PERLIN_OFFSET;
        copy.TRACK_PERLIN_FACTOR = this.TRACK_PERLIN_FACTOR;

        copy.TRACK_SURFACE_HEIGHT_OFFSET = this.TRACK_SURFACE_HEIGHT_OFFSET;
        copy.TRACK_TUNNEL_DEPTH = this.TRACK_TUNNEL_DEPTH;
        copy.TRACK_USE_BRIDGES = this.TRACK_USE_BRIDGES;
        copy.TRACK_BRIDGE_HEIGHT_THRESHOLD = this.TRACK_BRIDGE_HEIGHT_THRESHOLD;
        copy.TRACK_USE_SCAFFOLDING_SUPPORTS = this.TRACK_USE_SCAFFOLDING_SUPPORTS;
        copy.SCAFFOLDING_SPACING = this.SCAFFOLDING_SPACING;

        copy.STATION_SPACING = this.STATION_SPACING;
        copy.STATION_GENERATION_CHANCE = this.STATION_GENERATION_CHANCE;
        copy.STATION_MIN_CONNECTIONS = this.STATION_MIN_CONNECTIONS;
        copy.ALLOW_UNDERGROUND_STATIONS = this.ALLOW_UNDERGROUND_STATIONS;

        copy.TRACK_LANES = this.TRACK_LANES;
        copy.TRACK_BRANCH_CHANCE = this.TRACK_BRANCH_CHANCE;
        copy.TRACK_LOOP_GENERATION = this.TRACK_LOOP_GENERATION;
        copy.TRACK_MAX_CURVE_ANGLE = this.TRACK_MAX_CURVE_ANGLE;
        copy.TRACK_MIN_CURVE_RADIUS = this.TRACK_MIN_CURVE_RADIUS;

        copy.MAX_SURFACE_SLOPE = this.MAX_SURFACE_SLOPE;
        copy.FOLLOW_TERRAIN_CLOSELY = this.FOLLOW_TERRAIN_CLOSELY;
        copy.HEIGHT_SMOOTHING_FACTOR = this.HEIGHT_SMOOTHING_FACTOR;
        copy.MIN_TERRAIN_HEIGHT = this.MIN_TERRAIN_HEIGHT;
        copy.MAX_TERRAIN_HEIGHT = this.MAX_TERRAIN_HEIGHT;

        copy.TRACK_OUTSIDE_SPACE = this.TRACK_OUTSIDE_SPACE;
        copy.TRACK_TUNNEL_HEIGHT = this.TRACK_TUNNEL_HEIGHT;

        copy.DEFAULT_TRACK_MATERIAL = this.DEFAULT_TRACK_MATERIAL;
        copy.BIOME_SPECIFIC_MATERIALS = this.BIOME_SPECIFIC_MATERIALS;

        copy.TRACKS_ENABLED = this.TRACKS_ENABLED;
        copy.STATIONS_ENABLED = this.STATIONS_ENABLED;
        copy.GENERATE_IN_ALL_BIOMES = this.GENERATE_IN_ALL_BIOMES;
        copy.TRACK_SEED_OFFSET = this.TRACK_SEED_OFFSET;

        copy.DEBUG_MODE = this.DEBUG_MODE;
        copy.VISUALIZE_NETWORK = this.VISUALIZE_NETWORK;

        return copy;
    }
}
