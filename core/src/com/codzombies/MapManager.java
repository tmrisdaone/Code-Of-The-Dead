package com.codzombies;

import com.badlogic.gdx.math.Vector3;

/**
 * Manages the game map: walls, barricades, doors, wall-buy locations.
 * All geometry data is pre-defined — zero runtime allocation.
 */
public class MapManager {

    // ── Tile types ───────────────────────────────────────────
    public static final int TILE_EMPTY    = 0;
    public static final int TILE_WALL     = 1;
    public static final int TILE_DOOR     = 2;
    public static final int TILE_BARRIER  = 3;
    public static final int TILE_WALLBUY  = 4;

    // ── The map grid (12 x 12) ──────────────────────────────
    // Read-only template; per-instance copy is held in `mapData` so door-open
    // mutations don't leak across game restarts.
    private static final int[][] MAP_TEMPLATE = {
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 0, 0, 0, 0, 3, 1, 0, 0, 0, 0, 1},
            {1, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1},
            {1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1},
            {1, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 1},
            {1, 3, 0, 0, 2, 0, 0, 2, 0, 0, 3, 1},
            {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
            {1, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 1},
            {1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1},
            {1, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1},
            {1, 0, 0, 0, 0, 3, 1, 0, 0, 0, 0, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
    };

    public static final int MAP_SIZE = 12;

    /** Per-instance mutable copy of the map (door-open, barrier rebuild, etc.). */
    private int[][] mapData;

    // ── Barrier state ───────────────────────────────────────
    public static class Barrier {
        public int tilesX, tilesY;     // grid position
        public int boardsRemaining;    // 0..MAX_BARRIER_BOARDS
        public boolean breached;

        public Barrier(int tx, int ty) {
            this.tilesX = tx;
            this.tilesY = ty;
            this.boardsRemaining = Constants.MAX_BARRIER_BOARDS;
            this.breached = false;
        }
    }

    // ── Door state ──────────────────────────────────────────
    public static class Door {
        public int tilesX, tilesY;
        public boolean isOpen;
        public int cost;

        public Door(int tx, int ty, int cost) {
            this.tilesX = tx;
            this.tilesY = ty;
            this.isOpen = false;
            this.cost   = cost;
        }
    }

    // ── Wall Buy state ──────────────────────────────────────
    public static class WallBuy {
        public int tilesX, tilesY;
        public int weaponIndex;   // index into Weapon.WALL_BUY_WEAPONS
        public boolean purchased;

        public WallBuy(int tx, int ty, int weaponIdx) {
            this.tilesX      = tx;
            this.tilesY      = ty;
            this.weaponIndex = weaponIdx;
            this.purchased   = false;
        }

        public String getLabel() {
            if (weaponIndex < 0 || weaponIndex >= Weapon.WALL_BUY_WEAPONS.length)
                return "";
            return Weapon.WALL_BUY_WEAPONS[weaponIndex].wallBuyLabel;
        }
    }

    // ── Dynamic state arrays (bounded, no alloc after init) ──
    public final Barrier[] barriers;
    public final Door[]    doors;
    public final WallBuy[] wallBuys;
    public int barrierCount = 0;
    public int doorCount    = 0;
    public int wallBuyCount = 0;

    // ── Player position (from PlayerController) ──────────────
    private final PlayerController player;

    // ── Temp ─────────────────────────────────────────────────
    private final Vector3 tmp = new Vector3();

    public MapManager(PlayerController player) {
        this.player = player;
        copyMapFromTemplate();

        // Count interactables in map to size arrays
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                switch (mapData[y][x]) {
                    case TILE_BARRIER: barrierCount++; break;
                    case TILE_DOOR:    doorCount++;    break;
                    case TILE_WALLBUY: wallBuyCount++; break;
                }
            }
        }

        barriers = new Barrier[barrierCount];
        doors    = new Door[doorCount];
        wallBuys = new WallBuy[wallBuyCount];

        int bi = 0, di = 0, wi = 0;
        int doorCostIndex = 0;
        int[] doorCosts = {
                Constants.DOOR_COST_FIRST,
                Constants.DOOR_COST_SECOND,
                Constants.DOOR_COST_THIRD
        };

        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                switch (mapData[y][x]) {
                    case TILE_BARRIER:
                        barriers[bi++] = new Barrier(x, y);
                        break;
                    case TILE_DOOR:
                        doors[di++] = new Door(x, y,
                                doorCosts[Math.min(doorCostIndex++, doorCosts.length - 1)]);
                        break;
                    case TILE_WALLBUY:
                        wallBuys[wi++] = new WallBuy(x, y,
                                (wi - 1) % Weapon.WALL_BUY_WEAPONS.length);
                        break;
                }
            }
        }
    }

    /** Reset map state for a new game — restore tiles and interactables. */
    public void reset() {
        copyMapFromTemplate();
        for (int i = 0; i < barrierCount; i++) {
            barriers[i].boardsRemaining = Constants.MAX_BARRIER_BOARDS;
            barriers[i].breached = false;
        }
        for (int i = 0; i < doorCount; i++) {
            doors[i].isOpen = false;
        }
        for (int i = 0; i < wallBuyCount; i++) {
            wallBuys[i].purchased = false;
        }
    }

    /** Deep-copy MAP_TEMPLATE into the instance mapData array. */
    private void copyMapFromTemplate() {
        mapData = new int[MAP_SIZE][MAP_SIZE];
        for (int y = 0; y < MAP_SIZE; y++) {
            System.arraycopy(MAP_TEMPLATE[y], 0, mapData[y], 0, MAP_SIZE);
        }
    }

    // ── Updates ──────────────────────────────────────────────

    /** Called each frame to check zombie-barrier interaction and door proximity. */
    public void update(float dt, ZombieManager zombieManager) {
        // Check zombie proximity to barriers — zombies can break boards
        zombieManager.getZombiePool().forEachActive((zombie, idx) -> {
            if (!zombie.isActive()) return;
            for (int i = 0; i < barrierCount; i++) {
                Barrier b = barriers[i];
                // Convert tile to world coords
                float bx = b.tilesX * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f;
                float bz = b.tilesY * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f;
                float dx = zombie.position.x - bx;
                float dz = zombie.position.z - bz;
                float dist2 = dx * dx + dz * dz;
                if (dist2 < 4f && b.boardsRemaining > 0 && !b.breached) {
                    b.boardsRemaining--;
                    if (b.boardsRemaining <= 0) {
                        b.breached = true;
                    }
                }
            }
        });
    }

    // ── Interactions ─────────────────────────────────────────

    /** Player interacts at their position. Returns true if something happened. */
    public boolean interact() {
        float px = player.position.x;
        float pz = player.position.z;

        // Check proximity to doors
        for (int i = 0; i < doorCount; i++) {
            Door d = doors[i];
            if (d.isOpen) continue;
            float dx = (d.tilesX * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f) - px;
            float dz = (d.tilesY * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f) - pz;
            float dist2 = dx * dx + dz * dz;
            if (dist2 < 16f && player.points >= d.cost) {
                player.addPoints(-d.cost);
                d.isOpen = true;
                updateMapTile(d.tilesX, d.tilesY, TILE_EMPTY);
                return true;
            }
        }

        // Check proximity to barriers for rebuilding
        for (int i = 0; i < barrierCount; i++) {
            Barrier b = barriers[i];
            if (b.breached || b.boardsRemaining >= Constants.MAX_BARRIER_BOARDS) continue;
            float dx = (b.tilesX * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f) - px;
            float dz = (b.tilesY * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f) - pz;
            float dist2 = dx * dx + dz * dz;
            if (dist2 < 16f && player.points >= Constants.POINTS_PER_BOARD) {
                player.addPoints(-Constants.POINTS_PER_BOARD);
                b.boardsRemaining++;
                if (b.breached && b.boardsRemaining > 0) {
                    b.breached = false;
                }
                return true;
            }
        }

        return false;
    }

    /** Returns the nearest wall buy within interaction range, or -1. */
    public int getNearestWallBuyIndex() {
        float px = player.position.x;
        float pz = player.position.z;
        float bestDist = 16f;  // max interaction range squared
        int bestIdx = -1;

        for (int i = 0; i < wallBuyCount; i++) {
            WallBuy w = wallBuys[i];
            if (w.purchased) continue;
            float dx = (w.tilesX * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f) - px;
            float dz = (w.tilesY * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f) - pz;
            float dist2 = dx * dx + dz * dz;
            if (dist2 < bestDist) {
                bestDist = dist2;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    // ── Collision ────────────────────────────────────────────

    /** Returns true if the given position collides with a solid tile. */
    public boolean checkCollision(float x, float z) {
        int tx = (int)(x / Constants.TILE_SIZE);
        int tz = (int)(z / Constants.TILE_SIZE);
        if (tx < 0 || tx >= MAP_SIZE || tz < 0 || tz >= MAP_SIZE) return true;
        int tile = mapData[tz][tx];
        return tile == TILE_WALL || tile == TILE_DOOR || tile == TILE_BARRIER;
    }

    /** Returns the tile type at grid position. */
    public int getTile(int tx, int ty) {
        if (tx < 0 || tx >= MAP_SIZE || ty < 0 || ty >= MAP_SIZE) return TILE_WALL;
        return mapData[ty][tx];
    }

    /** Update a tile in the map data (e.g. after opening a door). */
    private void updateMapTile(int tx, int ty, int newType) {
        if (tx < 0 || tx >= MAP_SIZE || ty < 0 || ty >= MAP_SIZE) return;
        // Update the per-instance copy — never the shared template.
        mapData[ty][tx] = newType;
    }

    public int[][] getMapData() { return mapData; }
}
