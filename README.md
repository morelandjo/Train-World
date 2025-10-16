# Train World

A NeoForge mod for Minecraft 1.21.1 that generates Create mod train track networks across the entire world during terrain generation.

## Features

- **Automatic Track Generation**: Train tracks spawn naturally during world generation
- **Terrain-Aware Placement**: Tracks follow terrain contours, with bridges over gaps and tunnels through mountains
- **Deterministic Networks**: Same world seed always generates the same track layout
- **Configurable Density**: Adjust track spacing, station frequency, and branch chance
- **Create Integration**: Uses actual Create mod track blocks and scaffolding
- **Performance Optimized**: Cached calculations ensure minimal performance impact

## Requirements

- **Minecraft**: 1.21.1
- **NeoForge**: 21.1.211 or higher
- **Create**: Version 0.5.1 or higher (curse.maven:create-328085:6641610)

## Installation

1. Install NeoForge 1.21.1
2. Download the Create mod and place it in your `mods` folder
3. Download Train World and place it in your `mods` folder
4. Launch Minecraft

## How It Works

### Track Network Generation

Train World generates tracks using a grid-based system inspired by LostCities:

1. **Stations** are placed at configurable grid intersections (default: every 32 chunks)
2. **Major routes** connect stations in straight lines (North-South and East-West)
3. **Branch routes** are added with configurable probability to create organic networks
4. **Terrain integration** automatically adds bridges over gaps and tunnels through mountains

### Track Types

The mod generates various track types:
- **Straight tracks** (North-South, East-West)
- **Curves** (45° and 90°, minimum 8-block radius per Create's rules)
- **Junctions** (3-way and 4-way intersections)
- **Inclines** (45° slopes and S-curves for elevation changes)
- **Bridges** (with scaffolding pillars every 8 blocks)
- **Tunnels** (through mountains and rugged terrain)
- **Stations** (at grid intersections)

## Configuration

The mod comes with extensive configuration options. Edit `TrainWorldConfig.java` to customize:

### Network Spacing
```java
TRACK_SPACING_MASK = 15;           // Every 16 chunks
TRACK_NETWORK_DENSITY = 0.5f;      // 0.0 = sparse, 1.0 = dense
STATION_SPACING = 32;              // Distance between stations
```

### Terrain Integration
```java
TRACK_BRIDGE_HEIGHT_THRESHOLD = 5; // Min gap for bridge generation
TRACK_TUNNEL_DEPTH = 10;           // Tunnel depth below surface
SCAFFOLDING_SPACING = 8;           // Blocks between pillars
```

### Track Routing
```java
TRACK_BRANCH_CHANCE = 0.3f;        // Probability of branch routes
TRACK_LANES = 2;                   // Parallel tracks at major routes
TRACK_MIN_CURVE_RADIUS = 8;        // Minimum curve radius
```

## Development

### Building from Source

```bash
./gradlew build
```

Find the JAR in `build/libs/`

### Running Data Generation

```bash
./gradlew runData
```

## Credits

**Author**: vodmordia

**Inspiration**:
- **LostCities** by McJty - Grid-based generation algorithm
- **Create** by Simibubi - Track rules and mechanics

## License

All Rights Reserved
