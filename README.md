# Roguelike Dungeons - World Generation Reverse Engineering

## Overview
This document explains how Roguelike Dungeons generates and spawns dungeons in the world.

## Table of Contents
1. [Core Generation Flow](#core-generation-flow)
2. [Dungeon Spawn Prediction](#dungeon-spawn-prediction)
3. [Dungeon Structure Generation](#dungeon-structure-generation)
4. [Spawner Placement](#spawner-placement)
5. [Loot Generation](#loot-generation)
6. [Prediction Code](#prediction-code)
7. [Tower Type Prediction](#tower-type-prediction)

---

## Core Generation Flow

### Entry Point
- **Class**: `DungeonGenerator` (greymerk.roguelike.DungeonGenerator)
- **Method**: `generate(Random random, int chunkX, int chunkZ, World world, ...)`
- Called by Minecraft's world generation system for each chunk

### Generation Chain
```
DungeonGenerator.generate()
  └─> Dungeon.spawnInChunk()
      └─> Dungeon.canSpawnInChunk()  [Determines if dungeon spawns here]
          └─> Dungeon.generateNear()  [Finds valid location]
              └─> DungeonGenerator.generate()  [Generates structure]
```

---

## Dungeon Spawn Prediction

### Key Algorithm: `canSpawnInChunk(int chunkX, int chunkZ, IWorldEditor editor)`

Located in: `greymerk/roguelike/dungeon/Dungeon.java`

```java
public static boolean canSpawnInChunk(int chunkX, int chunkZ, IWorldEditor editor) {
    // 1. Check if natural spawning is enabled
    if (!RogueConfig.getBoolean(RogueConfig.DONATURALSPAWN)) {
        return false;
    }

    // 2. Get spawn frequency from config (default: 10)
    int frequency = RogueConfig.getInt(RogueConfig.SPAWNFREQUENCY);

    // 3. Calculate grid size
    int min = 8 * frequency / 10;  // default: 8 chunks
    int max = 32 * frequency / 10; // default: 32 chunks

    min = min < 2 ? 2 : min;
    max = max < 8 ? 8 : max;

    // 4. Normalize negative coordinates
    int tempX = chunkX < 0 ? chunkX - (max - 1) : chunkX;
    int tempZ = chunkZ < 0 ? chunkZ - (max - 1) : chunkZ;

    // 5. Get grid cell
    int m = tempX / max;
    int n = tempZ / max;

    // 6. Create seeded random for this grid cell
    Random r = editor.getSeededRandom(m, n, 10387312);

    // 7. Calculate base position
    m *= max;
    n *= max;

    // 8. Add random offset within grid cell
    m += r.nextInt(max - min);
    n += r.nextInt(max - min);

    // 9. Check if this chunk matches the calculated position
    return (chunkX == m && chunkZ == n);
}
```

### Spawn Grid Explanation

The world is divided into a grid where:
- **Grid Size**: Based on `spawnFrequency` config (default 10)
- **Default Grid**: 32x32 chunks (512x512 blocks)
- **Spawn Location**: Randomly placed within each grid cell using a **deterministic seed**

**Key Insight**: The spawn location in each grid cell is **deterministic** based on the world seed!

### World Seed Usage

The random seed for each grid cell is calculated as:
```java
public static Random getRandom(IWorldEditor editor, int x, int z) {
    long seed = editor.getSeed() * x * z;
    Random rand = new Random();
    rand.setSeed(seed);
    return rand;
}
```

Where `x` and `z` are the grid cell coordinates (m and n from above).

---

## Location Validation

After determining the spawn chunk, the code validates the actual block location:

### `generateNear(Random rand, int x, int z)`
```java
// Attempts to find valid location within 40-100 blocks of spawn chunk center
int attempts = 50;
for (int i = 0; i < attempts; i++) {
    Coord location = getNearbyCoord(rand, x, z, 40, 100);
    if (!validLocation(rand, location.getX(), location.getZ())) continue;
    // Generate dungeon here
}
```

### `validLocation(Random rand, int x, int z)` Checks:

1. **Biome Check**: Not in River, Beach, Mushroom Island, or Ocean biomes
2. **Y-Level Check**: Must find valid ground between `lowerLimit` (default: 60) and `upperLimit` (default: 100)
3. **Air Above**: Position must have air above ground level
4. **Not in Water**: Ground cannot be water
5. **No Structures Above**: 9x9 area 4 blocks above ground must be empty
6. **Solid Foundation**: At least part of 9x9 area 3 blocks below ground must be solid (max 8 air blocks)

---

## Dungeon Structure Generation

### Vertical Layout

- **Top Level Y**: Always 50 (constant `TOPLEVEL`)
- **Vertical Spacing**: 10 blocks between levels (constant `VERTICAL_SPACING`)
- **Number of Levels**: Configurable (default varies by settings)

### Level Layout
```
Y=50:  Level 0 (Top) + Tower
Y=40:  Level 1
Y=30:  Level 2
Y=20:  Level 3
Y=10:  Level 4 (Bottom)
```

### Room Generation

Each level uses one of two generation algorithms:
1. **CLASSIC**: Classic algorithm (LevelGeneratorClassic)
2. **MST**: Minimum Spanning Tree algorithm (LevelGeneratorMST)

The generator places:
- **Start Room**: Entry point for level
- **Random Rooms**: Based on `levelMaxRooms` config (default: 30)
- **Tunnels**: Connecting rooms
- **End Room**: Staircase to next level

---

## Spawner Placement

### Spawner Types
```java
CREEPER, CAVESPIDER, SPIDER, SKELETON, ZOMBIE, SILVERFISH,
ENDERMAN, WITCH, WITHERBOSS, BAT, LAVASLIME, BLAZE, SLIME,
PRIMEDTNT, PIGZOMBIE
```

### Common Spawners
By default, random spawners use: **SPIDER**, **SKELETON**, **ZOMBIE**

### Spawner Enhancement (Roguelike Spawners)

If `rogueSpawners` config is enabled, spawners get:
- **Potion Effect**: Slowness (ID: 4)
- **Amplifier**: Equals dungeon level (0-4)
- **Duration**: 10 ticks
- Makes mobs tougher at lower levels!

### Level-Based Difficulty

```java
public static int getLevel(int y) {
    if (y < 15) return 4;  // Hardest
    if (y < 25) return 3;
    if (y < 35) return 2;
    if (y < 45) return 1;
    return 0;              // Easiest
}
```

---

## Loot Generation

### Treasure Types
Defined in `Treasure` enum with different chest types:
- **STARTER**: Starting loot
- **COMMON**: Regular chests
- **RARE**: Rare loot
- **SPECIAL**: Special rewards
- etc.

### Loot Distribution

Loot is distributed using:
- **WeightedChoice**: Items have weights for random selection
- **Per-Level**: Different loot tables for each dungeon level
- **Loot Rules**: Defined by dungeon settings

---

## Prediction Code

### Standalone Dungeon Spawn Predictor

Create a new file to predict dungeon spawns:

```java
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class DungeonSpawnPredictor {

    public static class ChunkCoord {
        public final int x;
        public final int z;

        public ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public String toString() {
            return "Chunk(" + x + ", " + z + ") => Block(" +
                   (x * 16) + ", " + (z * 16) + ")";
        }
    }

    /**
     * Predicts dungeon spawn locations for a given world seed
     *
     * @param worldSeed The Minecraft world seed
     * @param spawnFrequency The spawn frequency config value (default: 10)
     * @param searchRadius How many grid cells to search in each direction
     * @return List of chunk coordinates where dungeons will spawn
     */
    public static List<ChunkCoord> predictDungeonSpawns(
            long worldSeed,
            int spawnFrequency,
            int searchRadius) {

        List<ChunkCoord> spawns = new ArrayList<>();

        // Calculate grid parameters
        int min = 8 * spawnFrequency / 10;
        int max = 32 * spawnFrequency / 10;
        min = min < 2 ? 2 : min;
        max = max < 8 ? 8 : max;

        // Search grid cells
        for (int gridX = -searchRadius; gridX <= searchRadius; gridX++) {
            for (int gridZ = -searchRadius; gridZ <= searchRadius; gridZ++) {

                // Get seeded random for this grid cell
                long seed = worldSeed * gridX * gridZ;
                Random rand = new Random(seed);

                // Calculate base position
                int m = gridX * max;
                int n = gridZ * max;

                // Add random offset
                m += rand.nextInt(max - min);
                n += rand.nextInt(max - min);

                spawns.add(new ChunkCoord(m, n));
            }
        }

        return spawns;
    }

    /**
     * Check if a specific chunk will have a dungeon spawn
     */
    public static boolean willDungeonSpawnInChunk(
            long worldSeed,
            int chunkX,
            int chunkZ,
            int spawnFrequency) {

        int min = 8 * spawnFrequency / 10;
        int max = 32 * spawnFrequency / 10;
        min = min < 2 ? 2 : min;
        max = max < 8 ? 8 : max;

        int tempX = chunkX < 0 ? chunkX - (max - 1) : chunkX;
        int tempZ = chunkZ < 0 ? chunkZ - (max - 1) : chunkZ;

        int m = tempX / max;
        int n = tempZ / max;

        long seed = worldSeed * m * n;
        Random rand = new Random(seed);

        m *= max;
        n *= max;

        m += rand.nextInt(max - min);
        n += rand.nextInt(max - min);

        return (chunkX == m && chunkZ == n);
    }

    /**
     * Get the nearby location offset from chunk spawn point
     * This approximates where the actual dungeon entrance will be
     */
    public static class BlockOffset {
        public final int x;
        public final int z;

        public BlockOffset(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    public static BlockOffset predictDungeonOffset(long worldSeed, int chunkX, int chunkZ) {
        // Use chunk coordinates as seed base
        long seed = worldSeed * chunkX * chunkZ;
        Random rand = new Random(seed);

        // generateNear uses 40-100 block radius
        int distance = 40 + rand.nextInt(100 - 40);
        double angle = rand.nextDouble() * 2 * Math.PI;

        int xOffset = (int) (Math.cos(angle) * distance);
        int zOffset = (int) (Math.sin(angle) * distance);

        return new BlockOffset(xOffset, zOffset);
    }

    // Example usage
    public static void main(String[] args) {
        long worldSeed = 123456789L; // Replace with your world seed
        int spawnFrequency = 10;      // Default config value
        int searchRadius = 5;         // Search 11x11 grid cells

        System.out.println("=== Roguelike Dungeon Spawn Predictor ===");
        System.out.println("World Seed: " + worldSeed);
        System.out.println("Spawn Frequency: " + spawnFrequency);
        System.out.println();

        List<ChunkCoord> spawns = predictDungeonSpawns(worldSeed, spawnFrequency, searchRadius);

        System.out.println("Found " + spawns.size() + " potential dungeon spawns:");
        System.out.println();

        for (ChunkCoord spawn : spawns) {
            System.out.println(spawn);

            // Predict approximate block location
            BlockOffset offset = predictDungeonOffset(worldSeed, spawn.x, spawn.z);
            int blockX = spawn.x * 16 + 4 + offset.x;
            int blockZ = spawn.z * 16 + 4 + offset.z;
            System.out.println("  Approximate entrance: (" + blockX + ", ~Y, " + blockZ + ")");
            System.out.println();
        }
    }
}
```

---

## Configuration Values

Default config values from `RogueConfig.java`:

| Config Key | Default Value | Description |
|------------|---------------|-------------|
| `doNaturalSpawn` | `true` | Enable natural dungeon spawning |
| `spawnFrequency` | `10` | Controls grid size (higher = rarer) |
| `upperLimit` | `100` | Maximum Y for ground search |
| `lowerLimit` | `60` | Minimum Y for ground search |
| `levelRange` | `80` | Range for level generation |
| `levelMaxRooms` | `30` | Max rooms per level |
| `levelScatter` | `10` | Room scatter distance |
| `rogueSpawners` | (varies) | Enable enhanced spawners |
| `generous` | `true` | More loot |
| `looting` | `0.085` | Looting chance modifier |

---

## Key Constants

```java
// DungeonGenerator.java
public static final int VERTICAL_SPACING = 10;  // Blocks between levels
public static final int TOPLEVEL = 50;           // Y coordinate of top level

// Dungeon.java - canSpawnInChunk
private static final int GRID_SEED = 10387312;   // Magic number for grid RNG
```

---

## Tower Type Prediction

### Biome-Based Tower Selection

Tower types are determined by the biome at the dungeon spawn location using Forge's BiomeDictionary system.

**See [BiomeBasedTowerPrediction.md](BiomeBasedTowerPrediction.md) for detailed information on:**
- How to get accurate biome data
- Mapping biomes to tower types
- Working with Biomes O' Plenty and other biome mods
- Implementation strategies for accurate tower type prediction

### Tower Type Mapping (Summary)

| Tower Type | Biome Dictionary Type | Example Biomes |
|------------|----------------------|----------------|
| PYRAMID | SANDY | Desert, BOP Outback |
| JUNGLE | JUNGLE | Jungle, BOP Tropical Rainforest |
| WITCH | SWAMP | Swampland, BOP Bayou |
| HOUSE | FOREST | Forest, BOP Woodland |
| ROGUE | PLAINS | Plains, Sunflower Plains |
| ETHO | MESA | Mesa, Mesa Plateau |
| ENIKO | MOUNTAIN | Extreme Hills |
| ROGUE | (default) | Any other biome |

### Key Classes

- **SettingsResolver** (`greymerk.roguelike.dungeon.settings.SettingsResolver`): Determines which settings to use based on biome
- **SpawnCriteria** (`greymerk.roguelike.dungeon.settings.SpawnCriteria`): Validates spawn location against biome criteria
- **Tower** enum (`greymerk.roguelike.dungeon.towers.Tower`): Defines all tower types

### Built-in Theme Settings

Each theme setting file defines:
- Biome type criteria (e.g., `BiomeDictionary.Type.SANDY`)
- Tower type (e.g., `Tower.PYRAMID`)
- Level themes and generation parameters

Theme setting files:
- `SettingsDesertTheme.java` → PYRAMID tower
- `SettingsJungleTheme.java` → JUNGLE tower
- `SettingsSwampTheme.java` → WITCH tower
- `SettingsForestTheme.java` → HOUSE tower
- `SettingsGrasslandTheme.java` → ROGUE tower
- `SettingsMesaTheme.java` → ETHO tower
- `SettingsMountainTheme.java` → ENIKO tower
- `SettingsTheme.java` → ROGUE tower (default)

---

## Summary

To predict dungeon spawns:

1. **Know world seed** - This is the key to everything
2. **Know the spawn frequency** - Default is 10, check config
3. **Calculate grid cells** - World divided into 32x32 chunk grids (with frequency 10)
4. **Use deterministic RNG** - Each grid cell has one spawn point calculated with `worldSeed * gridX * gridZ`
5. **Account for offset** - Actual dungeon is 40-100 blocks from chunk center
6. **Validate location** - Final position must pass biome/ground checks

The spawn system is **fully deterministic** based on world seed, making it possible to predict all dungeon locations without exploring!

---

## Additional Notes

### World Editor Seeded Random
The `editor.getSeededRandom(m, n, 10387312)` call uses Minecraft's `world.setRandomSeed(a, b, c)`:

```java
@Override
public Random getSeededRandom(int a, int b, int c) {
    return world.setRandomSeed(a, b, c);
}
```

Minecraft's `setRandomSeed(a, b, c)` creates a Random with:
```java
long seed = (a * 341873128712L + b * 132897987541L + worldSeed + c) * 14357617;
```

So for dungeon spawning:
```java
long seed = (gridX * 341873128712L + gridZ * 132897987541L + worldSeed + 10387312) * 14357617;
```

This makes the spawn locations **completely deterministic** and predictable!

### Chunk Coordinates
- Chunk coordinates are block coordinates divided by 16
- Chunk (0,0) contains blocks (0-15, 0-15)
- Chunk (-1,-1) contains blocks (-16 to -1, -16 to -1)

### Future Improvements
For even more accurate predictions, we would need to:
1. Get exact `getSeededRandom()` implementation
2. Implement biome lookup (requires Minecraft biome generator)
3. Implement terrain height validation
4. Check for other structures that might block spawns

