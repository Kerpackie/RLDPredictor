# Biome-Based Tower Type Prediction Guide

## Overview
To accurately predict which tower/dungeon type will spawn at a given location, we need to determine the biome at those coordinates and match it to the appropriate theme settings.

## Tower Type to Biome Mapping

Based on the builtin theme settings, the mapping is:

| Tower Type | Biome Dictionary Type | Example Biomes |
|------------|----------------------|----------------|
| **PYRAMID** | `SANDY` | Desert, Desert Hills, Desert M |
| **JUNGLE** | `JUNGLE` | Jungle, Jungle Hills, Jungle M |
| **WITCH** | `SWAMP` | Swampland, Swampland M |
| **HOUSE** | `FOREST` | Forest, Forest Hills, Birch Forest, etc. |
| **ROGUE** | `PLAINS` | Plains, Sunflower Plains |
| **ETHO** | `MESA` | Mesa, Mesa Plateau, Mesa Bryce |
| **ENIKO** (Bunker) | `MOUNTAIN` | Extreme Hills, Extreme Hills+ |
| **ROGUE** (default) | None of the above | Any other biome |

## The Challenge with Biomes

### Problems with Biome Generation:

1. Minecraft's biome generation uses multiple layers of noise generation, temperature/rainfall maps, and various transformations
2. Biome layout is deterministic based on world seed, but the algorithm is complex
3. To accurately determine biome at coordinates (X, Z), we need to:
   - Initialize the biome generator with the world seed
   - Run through all the biome generation layers
   - Query the specific coordinate

### Options for Getting Biome Data

## Option 1: Use MCP/Minecraft's Biome Generator (Standalone)

We can extract and use Minecraft's biome generation code:

```java
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.layer.GenLayer;

public class BiomePredictor {
    private WorldChunkManager biomeManager;

    public BiomePredictor(long worldSeed) {
        // Initialize biome generator for the world seed
        WorldType worldType = WorldType.DEFAULT;
        this.biomeManager = new WorldChunkManager(worldSeed, worldType);
    }

    public BiomeGenBase getBiomeAt(int blockX, int blockZ) {
        return biomeManager.getBiomeGenAt(blockX, blockZ);
    }

    public String predictTowerType(int blockX, int blockZ) {
        BiomeGenBase biome = getBiomeAt(blockX, blockZ);

        // Check biome dictionary types
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SANDY)) {
            return "PYRAMID";
        }
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.JUNGLE)) {
            return "JUNGLE";
        }
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SWAMP)) {
            return "WITCH";
        }
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.FOREST)) {
            return "HOUSE";
        }
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.PLAINS)) {
            return "ROGUE";
        }
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.MESA)) {
            return "ETHO";
        }
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.MOUNTAIN)) {
            return "ENIKO";
        }

        // Default
        return "ROGUE";
    }
}
```

**Pros:**
- Accurate biome prediction
- Uses official Minecraft code
- Works with vanilla biomes

**Cons:**
- Requires including Minecraft/Forge dependencies
- Doesn't automatically work with modded biomes (like Biomes O' Plenty)
- Somewhat heavyweight for a simple prediction tool

## Option 2: Use Biomes O' Plenty (BOP) Integration

Since we are using BOP, you need to account for BOP's custom biomes.

### BOP Biome Registration

BOP registers its biomes with the Forge BiomeDictionary. For example:
- `BiomeDictionary.registerBiomeType(BOPBiomes.tropical_rainforest, Type.JUNGLE, Type.DENSE, Type.WET)`

### Challenges with BOP:

1. **BOP uses its own biome generator** which differs from vanilla
2. **You'd need to include BOP as a dependency** to access its biome generation
3. **BOP biomes might have different Type tags** than we expect

### BOP-Aware Predictor

```java
import biomesoplenty.api.biome.BOPBiomes;
import net.minecraft.world.biome.BiomeGenBase;

public class BOPAwareBiomePredictor {

    public String predictTowerType(BiomeGenBase biome) {
        // BOP adds biomes to BiomeDictionary, so checking types still works
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SANDY)) {
            return "PYRAMID";  // e.g., BOP Outback, Xeric Shrubland
        }
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.JUNGLE)) {
            return "JUNGLE";  // e.g., BOP Tropical Rainforest, Bamboo Forest
        }
        if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.SWAMP)) {
            return "WITCH";  // e.g., BOP Bayou, Fen
        }
        // ... etc

        return "ROGUE";
    }
}
```

**Important:** BOP registers all its biomes with appropriate Type tags, so the BiomeDictionary checks still work!

There is also RWG Or other mods that don't add biomes, but manage the actual biome generation themselves.

We need to determine if these are going to effect our prediction accuracy.

