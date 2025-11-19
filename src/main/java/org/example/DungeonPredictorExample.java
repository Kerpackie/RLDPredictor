package org.example;

import java.util.List;
import org.example.DungeonSpawnPredictor.BlockOffset;
import org.example.DungeonSpawnPredictor.ChunkCoord;
import org.example.DungeonSpawnPredictor.DungeonSpawn;

/**
 * Example demonstrating how to use the DungeonSpawnPredictor
 *
 * This class contains various examples of using the prediction API
 * for different use cases.
 *
 * NOTE ON TOWER TYPE PREDICTION:
 * The tower types shown are pseudo-random approximations. For accurate tower type
 * prediction, you need biome data at the spawn coordinates. See BIOME_BASED_TOWER_PREDICTION.md
 * for information on how to integrate biome data into predictions.
 *
 * The base spawn point predictions (chunk locations) are 100% accurate.
 */
public class DungeonPredictorExample {

    /**
     * Example 1: Finding all dungeons near spawn (0, 0)
     */
    public static void findDungeonsNearSpawn(long worldSeed) {
        System.out.println("=== Example 1: Dungeons Near Spawn ===\n");

        int spawnFrequency = 10; // Default config value
        int searchRadius = 3;    // Search 7x7 grid cells around spawn

        // Calculate grid parameters
        int min = 8 * spawnFrequency / 10;
        int max = 32 * spawnFrequency / 10;
        min = Math.max(min, 2);
        max = Math.max(max, 8);

        System.out.println("Search Parameters:");
        System.out.println("  Spawn Frequency: " + spawnFrequency);
        System.out.println("  Grid Cell Size: " + max + " chunks (" + (max * 16) + " blocks)");
        System.out.println("  Search Radius: " + searchRadius + " grid cells");
        System.out.println("  Block Search Range: ±" + (searchRadius * max * 16) + " blocks\n");

        // Get base spawn points (these are accurate - the chunk spawn locations)
        List<DungeonSpawn> spawns = DungeonSpawnPredictor.predictDungeonBasePoints(
            worldSeed, spawnFrequency, searchRadius);

        // Sort by distance from spawn
        spawns.sort((a, b) -> {
            double distA = Math.sqrt(a.blockX * a.blockX + a.blockZ * a.blockZ);
            double distB = Math.sqrt(b.blockX * b.blockX + b.blockZ * b.blockZ);
            return Double.compare(distA, distB);
        });

        System.out.println("Found " + spawns.size() + " dungeon spawn chunks within " + searchRadius + " grid cells of spawn:");
        System.out.println("NOTE: Actual dungeon entrance is 40-100 blocks from the base spawn point in a random direction!");
        System.out.println("Expected dungeon near: x:-5, z:-620");
        System.out.println();

        // Print all dungeon base spawn points
        int count = 0;
        for (DungeonSpawn spawn : spawns) {
            count++;
            double dist = Math.sqrt(spawn.blockX * spawn.blockX + spawn.blockZ * spawn.blockZ);

            // Predict tower type based on the base spawn point
            String towerType = DungeonSpawnPredictor.predictTowerType(worldSeed, spawn.blockX, spawn.blockZ);

            // Check if this base point could reach expected location (-5, -620)
            int expectedX = -5;
            int expectedZ = -620;
            double distToExpected = Math.sqrt(
                (spawn.blockX - expectedX) * (spawn.blockX - expectedX) +
                (spawn.blockZ - expectedZ) * (spawn.blockZ - expectedZ));

            // Mark if within 100 blocks (max offset range)
            String marker = "";
            if (distToExpected <= 100) {
                marker = " *** COULD REACH EXPECTED (within 100 blocks) ***";
            } else if (distToExpected <= 150) {
                marker = " ** NEARBY **";
            }

            System.out.printf("%3d. [%-8s] Base Point: (%6d, %6d) - Distance from spawn: %6.0f blocks - Chunk: (%4d, %4d) - Search radius: 40-100 blocks%s\n",
                count, towerType, spawn.blockX, spawn.blockZ, dist, spawn.chunk.x, spawn.chunk.z, marker);
        }

        System.out.println();
    }

    /**
     * Example 2: Finding nearest dungeon to a specific location
     */
    public static void findNearestToPlayer(long worldSeed, int playerX, int playerZ) {
        System.out.println("=== Example 2: Nearest Dungeon to Player ===\n");

        int spawnFrequency = 10;
        int searchRadius = 10; // Search larger area

        DungeonSpawn nearest = DungeonSpawnPredictor.findNearestDungeon(
            worldSeed, spawnFrequency, playerX, playerZ, searchRadius);

        if (nearest == null) {
            System.out.println("No dungeons found within search radius!");
            return;
        }

        double dx = nearest.blockX - playerX;
        double dz = nearest.blockZ - playerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Calculate direction
        String direction;
        if (Math.abs(dx) > Math.abs(dz)) {
            direction = dx > 0 ? "East" : "West";
        } else {
            direction = dz > 0 ? "South" : "North";
        }

        System.out.println("Player at: " + playerX + ", " + playerZ);
        System.out.println("Nearest dungeon type: " + nearest.towerType);
        System.out.println("Location: " + nearest.blockX + ", " + nearest.blockZ);
        System.out.println("Distance: " + (int)distance + " blocks " + direction);
        System.out.println("Chunk: " + nearest.chunk.x + ", " + nearest.chunk.z);
        System.out.println();
    }

    /**
     * Example 3: Checking if player's current chunk has a dungeon
     */
    public static void checkCurrentChunk(long worldSeed, int playerX, int playerZ) {
        System.out.println("=== Example 3: Check Current Chunk ===\n");

        // Convert player position to chunk coordinates
        int chunkX = playerX >> 4; // Divide by 16
        int chunkZ = playerZ >> 4;

        int spawnFrequency = 10;

        boolean willSpawn = DungeonSpawnPredictor.willDungeonSpawnInChunk(
            worldSeed, chunkX, chunkZ, spawnFrequency);

        System.out.println("Player position: " + playerX + ", " + playerZ);
        System.out.println("Current chunk: " + chunkX + ", " + chunkZ);
        System.out.println("Dungeon in this chunk: " + (willSpawn ? "YES!" : "No"));

        if (willSpawn) {
            BlockOffset offset = DungeonSpawnPredictor.predictDungeonOffset(
                worldSeed, chunkX, chunkZ);
            int entranceX = chunkX * 16 + 4 + offset.x;
            int entranceZ = chunkZ * 16 + 4 + offset.z;

            System.out.println("Predicted entrance: " + entranceX + ", ~50, " + entranceZ);
        }

        System.out.println();
    }

    /**
     * Example 4: Creating a dungeon map for a region
     */
    public static void createDungeonMap(long worldSeed, int centerX, int centerZ, int radius) {
        System.out.println("=== Example 4: Dungeon Map ===\n");

        int spawnFrequency = 10;

        // Convert center to chunk, then to grid cell
        int centerChunkX = centerX >> 4;
        int centerChunkZ = centerZ >> 4;

        System.out.println("Creating map centered at: " + centerX + ", " + centerZ);
        System.out.println("Search radius: " + radius + " grid cells\n");

        List<DungeonSpawn> spawns = DungeonSpawnPredictor.predictDungeonSpawns(
            worldSeed, spawnFrequency, radius);

        // Filter to dungeons within actual coordinate range
        int maxDist = radius * 32 * 16; // Grid cells * chunks * blocks
        int count = 0;

        System.out.println("Dungeons within " + maxDist + " blocks:");
        System.out.println("----------------------------------------");

        for (DungeonSpawn spawn : spawns) {
            int dx = spawn.blockX - centerX;
            int dz = spawn.blockZ - centerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist <= maxDist) {
                count++;
                System.out.printf("%3d. [%-8s] (%6d, %6d) - %4.0f blocks - Chunk (%4d, %4d)\n",
                    count, spawn.towerType, spawn.blockX, spawn.blockZ, dist, spawn.chunk.x, spawn.chunk.z);
            }
        }

        System.out.println("\nTotal dungeons in range: " + count);
        System.out.println();
    }

    /**
     * Example 5: Grid analysis - understanding spawn distribution
     */
    public static void analyzeSpawnGrid(long worldSeed) {
        System.out.println("=== Example 5: Spawn Grid Analysis ===\n");

        int spawnFrequency = 10;

        // Calculate grid parameters
        int min = 8 * spawnFrequency / 10;
        int max = 32 * spawnFrequency / 10;
        min = Math.max(min, 2);
        max = Math.max(max, 8);

        System.out.println("Spawn Frequency: " + spawnFrequency);
        System.out.println("Grid Cell Size: " + max + " chunks (" + (max * 16) + " blocks)");
        System.out.println("Spawn Offset Range: " + min + " to " + max + " chunks");
        System.out.println();

        // Analyze first 3x3 grid cells
        System.out.println("First 3x3 grid cells around spawn:");
        System.out.println("Grid(X,Z) -> Chunk(X,Z) -> Block(X,Z)");
        System.out.println("--------------------------------------------------");

        for (int gridX = -1; gridX <= 1; gridX++) {
            for (int gridZ = -1; gridZ <= 1; gridZ++) {
                long seed = (gridX * 341873128712L + gridZ * 132897987541L +
                            worldSeed + 10387312) * 14357617;
                java.util.Random rand = new java.util.Random(seed);

                int m = gridX * max + rand.nextInt(max - min);
                int n = gridZ * max + rand.nextInt(max - min);

                int blockX = m * 16 + 8;
                int blockZ = n * 16 + 8;

                System.out.printf("Grid(%2d,%2d) -> Chunk(%4d,%4d) -> Block(%6d,%6d)\n",
                    gridX, gridZ, m, n, blockX, blockZ);
            }
        }

        System.out.println();
    }

    /**
     * Example 6: Testing prediction accuracy
     */
    public static void testPredictionAccuracy(long worldSeed) {
        System.out.println("=== Example 6: Testing Prediction Accuracy ===\n");

        int spawnFrequency = 10;

        // Predict a specific chunk
        int testChunkX = 32;
        int testChunkZ = -45;

        boolean predicted = DungeonSpawnPredictor.willDungeonSpawnInChunk(
            worldSeed, testChunkX, testChunkZ, spawnFrequency);

        System.out.println("Test Chunk: " + testChunkX + ", " + testChunkZ);
        System.out.println("Prediction: " + (predicted ? "WILL SPAWN" : "Won't spawn"));

        // Verify by checking all spawns in area
        List<ChunkCoord> allSpawns = DungeonSpawnPredictor.predictDungeonChunks(
            worldSeed, spawnFrequency, 5);

        ChunkCoord testChunk = new ChunkCoord(testChunkX, testChunkZ);
        boolean foundInList = false;

        for (ChunkCoord spawn : allSpawns) {
            if (spawn.equals(testChunk)) {
                foundInList = true;
                break;
            }
        }

        System.out.println("Found in spawn list: " + foundInList);
        System.out.println("Verification: " + (predicted == foundInList ? "PASS ✓" : "FAIL ✗"));
        System.out.println();
    }

    /**
     * Main method - Run all examples
     */
    public static void main(String[] args) {
        // Use your world seed here
        long worldSeed = 123456789L;

        // Change this to your actual world seed for real results!
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   Roguelike Dungeon Predictor Examples      ║");
        System.out.println("║   World Seed: " + String.format("%-28d", worldSeed) + "║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        // Run all examples
        findDungeonsNearSpawn(worldSeed);

        /*
        findNearestToPlayer(worldSeed, 500, -300);
        checkCurrentChunk(worldSeed, 520, -1248);
        createDungeonMap(worldSeed, 0, 0, 2);
        analyzeSpawnGrid(worldSeed);
        testPredictionAccuracy(worldSeed);*/

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   All examples completed!                   ║");
        System.out.println("╚══════════════════════════════════════════════╝");
    }
}

