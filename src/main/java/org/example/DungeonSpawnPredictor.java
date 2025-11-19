package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility class to predict where Roguelike Dungeons will spawn based on world seed.
 *
 * This reverse engineers the spawn algorithm to allow players/admins to predict
 * dungeon locations without exploring the world.
 *
 * @author Reverse Engineered from Roguelike Dungeons source
 */
public class DungeonSpawnPredictor {

    /**
     * Represents a chunk coordinate where a dungeon may spawn
     */
    public static class ChunkCoord {
        public final int x;
        public final int z;

        public ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        /**
         * Convert chunk coordinates to block coordinates (center of chunk)
         */
        public int getBlockX() {
            return x * 16 + 8;
        }

        public int getBlockZ() {
            return z * 16 + 8;
        }

        @Override
        public String toString() {
            return "Chunk(" + x + ", " + z + ") => Block(" + getBlockX() + ", " + getBlockZ() + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ChunkCoord)) return false;
            ChunkCoord other = (ChunkCoord) obj;
            return this.x == other.x && this.z == other.z;
        }

        @Override
        public int hashCode() {
            return x * 31 + z;
        }
    }

    /**
     * Represents a predicted dungeon spawn location with block coordinates
     */
    public static class DungeonSpawn {
        public final ChunkCoord chunk;
        public final int blockX;
        public final int blockZ;
        public final int approximateY;
        public final String towerType; // Type of tower/dungeon variant

        public DungeonSpawn(ChunkCoord chunk, int blockX, int blockZ) {
            this(chunk, blockX, blockZ, "UNKNOWN");
        }

        public DungeonSpawn(ChunkCoord chunk, int blockX, int blockZ, String towerType) {
            this.chunk = chunk;
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.approximateY = 50; // TOPLEVEL constant
            this.towerType = towerType;
        }

        @Override
        public String toString() {
            if (towerType != null && !towerType.equals("UNKNOWN")) {
                return "Dungeon [" + towerType + "] at (" + blockX + ", ~" + approximateY + ", " + blockZ + ") in " + chunk;
            }
            return "Dungeon at (" + blockX + ", ~" + approximateY + ", " + blockZ + ") in " + chunk;
        }
    }

    /**
     * Represents an offset from the chunk spawn point
     */
    public static class BlockOffset {
        public final int x;
        public final int z;

        public BlockOffset(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    /**
     * Predicts all dungeon spawn chunk coordinates within a search radius.
     * This uses the exact algorithm from Dungeon.canSpawnInChunk()
     *
     * @param worldSeed The Minecraft world seed
     * @param spawnFrequency The spawn frequency config value (default: 10)
     * @param searchRadius How many grid cells to search in each direction from spawn
     * @return List of chunk coordinates where dungeons will spawn
     */
    public static List<ChunkCoord> predictDungeonChunks(
            long worldSeed,
            int spawnFrequency,
            int searchRadius) {

        List<ChunkCoord> spawns = new ArrayList<ChunkCoord>();

        // Calculate grid parameters (from canSpawnInChunk)
        int min = 8 * spawnFrequency / 10;
        int max = 32 * spawnFrequency / 10;
        min = min < 2 ? 2 : min;
        max = max < 8 ? 8 : max;

        // Search grid cells around spawn
        for (int gridX = -searchRadius; gridX <= searchRadius; gridX++) {
            for (int gridZ = -searchRadius; gridZ <= searchRadius; gridZ++) {

                // This matches the getSeededRandom call in the original code
                // Uses Minecraft's world.setRandomSeed(a, b, c) algorithm
                // Formula: (a * 341873128712L + b * 132897987541L + worldSeed + c) * 14357617
                long seed = (gridX * 341873128712L + gridZ * 132897987541L + worldSeed + 10387312) * 14357617;
                Random rand = new Random(seed);

                // Calculate base position (grid cell * max)
                int m = gridX * max;
                int n = gridZ * max;

                // Add random offset within the cell
                m += rand.nextInt(max - min);
                n += rand.nextInt(max - min);

                spawns.add(new ChunkCoord(m, n));
            }
        }

        return spawns;
    }

    /**
     * Check if a specific chunk will have a dungeon spawn.
     * This is the exact reverse-engineered algorithm from Dungeon.canSpawnInChunk()
     *
     * @param worldSeed The Minecraft world seed
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param spawnFrequency The spawn frequency config value (default: 10)
     * @return true if a dungeon will spawn in this chunk
     */
    public static boolean willDungeonSpawnInChunk(
            long worldSeed,
            int chunkX,
            int chunkZ,
            int spawnFrequency) {

        // Calculate grid parameters
        int min = 8 * spawnFrequency / 10;
        int max = 32 * spawnFrequency / 10;
        min = min < 2 ? 2 : min;
        max = max < 8 ? 8 : max;

        // Normalize negative coordinates
        int tempX = chunkX < 0 ? chunkX - (max - 1) : chunkX;
        int tempZ = chunkZ < 0 ? chunkZ - (max - 1) : chunkZ;

        // Get grid cell
        int m = tempX / max;
        int n = tempZ / max;

        // Create seeded random for this grid cell
        // Uses Minecraft's world.setRandomSeed(a, b, c) algorithm
        long seed = (m * 341873128712L + n * 132897987541L + worldSeed + 10387312) * 14357617;
        Random rand = new Random(seed);

        // Calculate base position
        m *= max;
        n *= max;

        // Add random offset
        m += rand.nextInt(max - min);
        n += rand.nextInt(max - min);

        // Check if this chunk matches
        return (chunkX == m && chunkZ == n);
    }

    /**
     * Predicts the approximate block offset from the chunk spawn point.
     * This uses the algorithm from Dungeon.generateNear() and getNearbyCoord()
     *
     * Note: The actual dungeon may not spawn here if the location fails validation,
     * but this gives the first attempted location.
     *
     * @param worldSeed The world seed
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return BlockOffset indicating where the dungeon entrance will likely be
     */
    public static BlockOffset predictDungeonOffset(long worldSeed, int chunkX, int chunkZ) {
        // Convert chunk to block coordinates (center of chunk + 4 from spawnInChunk)
        int x = chunkX * 16 + 4;
        int z = chunkZ * 16 + 4;

        // Use the same seed calculation as Dungeon.getRandom()
        long seed = worldSeed * x * z;
        Random rand = new Random(seed);

        // getNearbyCoord uses 40-100 block radius
        int min = 40;
        int max = 100;
        int distance = min + rand.nextInt(max - min);
        double angle = rand.nextDouble() * 2 * Math.PI;

        int xOffset = (int) (Math.cos(angle) * distance);
        int zOffset = (int) (Math.sin(angle) * distance);

        return new BlockOffset(xOffset, zOffset);
    }

    /**
     * Predicts dungeon spawn base points (chunk spawn locations).
     * These are the initial positions (chunk * 16 + 4) before the random 40-100 block offset.
     * The actual dungeon will be 40-100 blocks away from these points in a random direction.
     *
     * @param worldSeed The Minecraft world seed
     * @param spawnFrequency The spawn frequency config value (default: 10)
     * @param searchRadius How many grid cells to search
     * @return List of predicted dungeon spawn base points
     */
    public static List<DungeonSpawn> predictDungeonBasePoints(
            long worldSeed,
            int spawnFrequency,
            int searchRadius) {

        List<DungeonSpawn> spawns = new ArrayList<DungeonSpawn>();
        List<ChunkCoord> chunks = predictDungeonChunks(worldSeed, spawnFrequency, searchRadius);

        for (ChunkCoord chunk : chunks) {
            // Base spawn point without random offset
            int blockX = chunk.x * 16 + 4;
            int blockZ = chunk.z * 16 + 4;

            // Type prediction (approximate)
            String towerType = "UNKNOWN";

            spawns.add(new DungeonSpawn(chunk, blockX, blockZ, towerType));
        }

        return spawns;
    }

    /**
     * Predicts full dungeon spawn locations with block coordinates.
     * Combines chunk prediction with offset calculation.
     *
     * @param worldSeed The Minecraft world seed
     * @param spawnFrequency The spawn frequency config value (default: 10)
     * @param searchRadius How many grid cells to search
     * @return List of predicted dungeon spawn locations
     */
    public static List<DungeonSpawn> predictDungeonSpawns(
            long worldSeed,
            int spawnFrequency,
            int searchRadius) {

        List<DungeonSpawn> spawns = new ArrayList<DungeonSpawn>();
        List<ChunkCoord> chunks = predictDungeonChunks(worldSeed, spawnFrequency, searchRadius);

        for (ChunkCoord chunk : chunks) {
            BlockOffset offset = predictDungeonOffset(worldSeed, chunk.x, chunk.z);

            // Calculate final block coordinates
            int blockX = chunk.x * 16 + 4 + offset.x;
            int blockZ = chunk.z * 16 + 4 + offset.z;

            // Predict tower type
            String towerType = predictTowerType(worldSeed, blockX, blockZ);

            spawns.add(new DungeonSpawn(chunk, blockX, blockZ, towerType));
        }

        return spawns;
    }

    /**
     * Predicts the approximate tower/dungeon type based on coordinate hash.
     * This is a simplified prediction - actual type depends on biome and config.
     *
     * Tower types: ROGUE, ENIKO, ETHO, PYRAMID, JUNGLE, WITCH, HOUSE, BUNKER
     * Biome themes: DESERT(Pyramid), JUNGLE(Jungle), SWAMP(Witch), etc.
     *
     * Note: Without biome data, this uses coordinate-based hashing for variety.
     * In the real game, biome determines theme which determines tower type.
     *
     * @param worldSeed The world seed
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Predicted tower type name
     */
    public static String predictTowerType(long worldSeed, int blockX, int blockZ) {
        // Use coordinate hash to simulate biome-based selection
        // This matches how the game uses seeded random for settings selection
        long seed = worldSeed * blockX * blockZ;
        Random rand = new Random(seed);

        // Tower types available in the game
        String[] towerTypes = {
            "ROGUE",      // Default/grassland
            "PYRAMID",    // Desert biome
            "JUNGLE",     // Jungle biome
            "WITCH",      // Swamp biome
            "HOUSE",      // Plains/village
            "BUNKER",     // Mountain/extreme hills
            "ETHO",       // Forest
            "ENIKO"       // Special/rare
        };

        // Weight based on general biome distribution (simplified)
        // ROGUE is most common (default), others are biome-specific
        int[] weights = {30, 10, 10, 10, 15, 10, 10, 5};
        int totalWeight = 0;
        for (int w : weights) totalWeight += w;

        int selection = rand.nextInt(totalWeight);
        int cumulative = 0;

        for (int i = 0; i < towerTypes.length; i++) {
            cumulative += weights[i];
            if (selection < cumulative) {
                return towerTypes[i];
            }
        }

        return "ROGUE"; // Default fallback
    }

    /**
     * Converts block coordinates to chunk coordinates
     */
    public static ChunkCoord blockToChunk(int blockX, int blockZ) {
        return new ChunkCoord(blockX >> 4, blockZ >> 4);
    }

    /**
     * Finds the nearest predicted dungeon spawn to given coordinates
     */
    public static DungeonSpawn findNearestDungeon(
            long worldSeed,
            int spawnFrequency,
            int playerX,
            int playerZ,
            int searchRadius) {

        List<DungeonSpawn> spawns = predictDungeonSpawns(worldSeed, spawnFrequency, searchRadius);

        DungeonSpawn nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (DungeonSpawn spawn : spawns) {
            double dx = spawn.blockX - playerX;
            double dz = spawn.blockZ - playerZ;
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < minDistance) {
                minDistance = distance;
                nearest = spawn;
            }
        }

        return nearest;
    }

    /**
     * Example usage and testing method
     */
    public static void main(String[] args) {
        // Example configuration
        long worldSeed = 123456789L;
        int spawnFrequency = 10;
        int searchRadius = 5;

        System.out.println("=== Roguelike Dungeon Spawn Predictor ===");
        System.out.println("World Seed: " + worldSeed);
        System.out.println("Spawn Frequency: " + spawnFrequency);
        System.out.println("Search Radius: " + searchRadius + " grid cells");
        System.out.println();

        // Predict chunk spawns
        List<ChunkCoord> chunks = predictDungeonChunks(worldSeed, spawnFrequency, searchRadius);
        System.out.println("Found " + chunks.size() + " potential dungeon spawn chunks:");
        System.out.println();

        // Predict full spawn locations
        List<DungeonSpawn> spawns = predictDungeonSpawns(worldSeed, spawnFrequency, searchRadius);

        int count = 0;
        for (DungeonSpawn spawn : spawns) {
            count++;
            System.out.println("#" + count + ": " + spawn);

            // Verify chunk calculation
            boolean willSpawn = willDungeonSpawnInChunk(worldSeed, spawn.chunk.x, spawn.chunk.z, spawnFrequency);
            System.out.println("     Verification: " + (willSpawn ? "PASS" : "FAIL"));
            System.out.println();

            if (count >= 10) {
                System.out.println("... and " + (spawns.size() - 10) + " more");
                break;
            }
        }

        // Find nearest to spawn (0, 0)
        System.out.println("\n=== Nearest Dungeon to Spawn ===");
        DungeonSpawn nearest = findNearestDungeon(worldSeed, spawnFrequency, 0, 0, searchRadius);
        if (nearest != null) {
            System.out.println(nearest);
            double distance = Math.sqrt(nearest.blockX * nearest.blockX + nearest.blockZ * nearest.blockZ);
            System.out.println("Distance from spawn: " + (int)distance + " blocks");
        }
    }
}

