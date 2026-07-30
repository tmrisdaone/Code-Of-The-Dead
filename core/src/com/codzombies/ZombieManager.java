package com.codzombies;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/**
 * Owns the wave lifecycle, zombie pool, and per-zombie AI steering + melee.
 * Also resolves the player's hitscan Shot against active zombies (ray vs AABB).
 *
 * Round model (COD Zombies-style):
 *   - Round 1: zombiesForRound(1) = 18 spawned gradually across the round.
 *   - Each round increases count per the wiki formula (see Constants),
 *     and health/speed scale mildly. Round N completes when all spawned
 *     zombies are dead and the spawn budget is exhausted.
 *
 * The manager is pure model: it does no rendering. The GameScreen reads
 * zombies() each frame to render instances, and feeds the manager back
 * kill events and damage events.
 */
public class ZombieManager {

    // ── Per-zombie entity (pooled — no GC churn during gameplay) ──
    /** A single zombie. Package-visible fields so the manager can mutate it cheaply. */
    public static final class Zombie implements Pool.Poolable {
        public final Vector3 position = new Vector3();
        public float health;
        public float speed;
        public float attackTimer;   // cooldown remaining before next melee swing
        public boolean alive;       // in-play and not yet killed

        // Hit-flash alpha for the renderer (decays to 0).
        public float hitFlash;

        Zombie() { reset(); }

        @Override public void reset() {
            position.set(0f, 0f, 0f);
            health = 0f;
            speed  = 0f;
            attackTimer = 0f;
            alive = false;
            hitFlash = 0f;
        }

        public void spawn(Vector3 p, float hp, float sp) {
            position.set(p);
            health = hp;
            speed  = sp;
            attackTimer = 1f;     // brief delay before the first swing
            alive = true;
            hitFlash = 0f;
        }

        /** Advance AI by dt. Pure steering toward the player; no pathing yet. */
        public void updateAI(float dt, Vector3 playerPos) {
            if (!alive) return;

            // Decay hit-flash regardless of state.
            if (hitFlash > 0f) hitFlash = Math.max(0f, hitFlash - dt * 4f);

            Vector3 toPlayer = tmp.set(playerPos).sub(position);
            float len = toPlayer.len();
            if (len < 0.0001f) return;

            // If within melee range, stop moving and damage the player
            // via the callback (the GameScreen wires player.takeDamage).
            if (len <= Constants.ZOMBIE_ATTACK_RANGE) {
                // (Melee handled by the manager, not here, to avoid a
                // back-reference Player inside Zombie.)
                return;
            }

            // Steer: move at `speed` straight toward the player along the XZ plane.
            // Y is kept at 0 (zombies walk on the floor).
            float nx = toPlayer.x / len;
            float nz = toPlayer.z / len;
            float stepZ = speed * dt;
            position.x += nx * stepZ;
            position.z += nz * stepZ;
            position.y  = 0f;
        }
    }

    // ── Scratch vector (reused inside Zombie.updateAI to avoid per-frame allocs) ──
    private static final Vector3 tmp = new Vector3();

    // ── Pools + collections (LibGDX collections, zero-alloc iteration) ──
    private final Pool<Zombie> pool = new Pool<Zombie>(Constants.ZOMBIE_MAX_POOL) {
        @Override protected Zombie newObject() { return new Zombie(); }
    };
    /** Active (in-play) zombies. There may also be dead-but-not-yet-reaped zombies briefly. */
    private final Array<Zombie> zombies = new Array<>(true, 32, Zombie.class);

    /** Melee callback so Zombie doesn't need a Player reference. */
    public interface MeleeSink { void onZombieMelee(float damage); }

    // ── Round / spawning state ──
    private int  round      = 0;
    private int  toSpawn    = 0;    // zombies still to spawn this round
    private int  aliveCount = 0;    // currently alive (drives round completion)
    private float spawnTimer = 0f;  // inter-spawn delay accumulator
    private float roundInterlude = 0f;  // delay between rounds

    /** Spawns are picked from these world positions (edge of the arena). */
    private final Array<Vector3> spawnPoints = new Array<>(true, 8, Vector3.class);

    public ZombieManager() {
        addSpawnPoint(new Vector3(-22f,  0f, -22f));
        addSpawnPoint(new Vector3( 22f,  0f, -22f));
        addSpawnPoint(new Vector3(-22f,  0f,  22f));
        addSpawnPoint(new Vector3( 22f,  0f,  22f));
        addSpawnPoint(new Vector3(  0f,  0f, -24f));
    }

    public void addSpawnPoint(Vector3 p) { spawnPoints.add(new Vector3(p)); }

    // =================================================================
    //  MAIN UPDATE
    // =================================================================

    /**
     * Advance the wave sim by dt.
     * @param playerPos Live player world position (for AI steering + melee range).
     * @param melee    Sink that takes damage when a zombie melees the player.
     */
    public void update(float dt, Vector3 playerPos, MeleeSink melee) {
        // ── Begin the first round immediately on the very first update ──
        if (round == 0 && toSpawn == 0 && aliveCount == 0) {
            startNextRound();
            return;       // start round 1; begin spawning next frame
        }

        // ── Interlude between rounds ──
        // While the breather is active, count it down. When it expires we
        // start the next round and immediately return so we don't also
        // spawn+simulate on the same frame the round flips.
        if (roundInterlude > 0f) {
            roundInterlude -= dt;
            if (roundInterlude <= 0f) {
                roundInterlude = 0f;
                startNextRound();
            }
            return;                        // freeze spawning/AI during the breather
        }

        // ── Spawn pacing ──
        if (toSpawn > 0) {
            // Spawn delay shrinks as rounds progress, but never below 0.4s,
            // so the player isn't swarmed all at once even late game.
            float delay = Math.max(0.4f, 2.2f - round * 0.07f);
            spawnTimer += dt;
            if (spawnTimer >= delay) {
                spawnOne(playerPos);
                spawnTimer = 0f;
            }
        }

        // ── AI + melee (backwards so we can free on death) ──
        float meleeRangeSq = Constants.ZOMBIE_ATTACK_RANGE * Constants.ZOMBIE_ATTACK_RANGE;
        for (int i = zombies.size - 1; i >= 0; i--) {
            Zombie z = zombies.get(i);
            if (!z.alive) continue;

            z.updateAI(dt, playerPos);

            // Melee if the zombie reached the player.
            float dx = z.position.x - playerPos.x;
            float dz = z.position.z - playerPos.z;
            if (dx * dx + dz * dz <= meleeRangeSq) {
                if (z.attackTimer <= 0f) {
                    melee.onZombieMelee(Constants.ZOMBIE_ATTACK_DAMAGE);
                    z.attackTimer = Constants.ZOMBIE_ATTACK_COOLDOWN;
                }
            }
            if (z.attackTimer > 0f) z.attackTimer -= dt;

            // Reap dead zombies
            if (z.health <= 0f) {
                z.alive = false;
                zombies.removeIndex(i);
                pool.free(z);
                aliveCount = Math.max(0, aliveCount - 1);
            }
        }

        // ── Round completion: all zombies dead and spawn budget spent ──
        if (toSpawn == 0 && aliveCount == 0) {
            roundInterlude = 4f;     // breather; interlude block above starts next round
        }
    }

    private void startNextRound() {
        round++;
        toSpawn    = Constants.zombieCountForRound(round);
        aliveCount = 0;
        spawnTimer = 0f;
    }

    /** Pick a random spawn point and summon one zombie scaled to the current round. */
    private void spawnOne(Vector3 playerPos) {
        // Choose the spawn farthest from the player — COD prefers giving the
        // player a beat before the new zombie arrives.
        Vector3 spawn = spawnPoints.first();
        float bestSq = -1f;
        for (int i = 0; i < spawnPoints.size; i++) {
            Vector3 p = spawnPoints.get(i);
            float dx = p.x - playerPos.x;
            float dz = p.z - playerPos.z;
            float sq = dx * dx + dz * dz;
            if (sq > bestSq) { bestSq = sq; spawn = p; }
        }

        float hp = Constants.zombieHealthForRound(round);
        float sp = Constants.ZOMBIE_SPEED * (1f + 0.03f * Math.max(0, round - 1)); // mild ramp

        Zombie z = pool.obtain();
        z.spawn(spawn, hp, sp);
        zombies.add(z);
        toSpawn--;
        aliveCount++;
    }

    // =================================================================
    //  HITSCAN
    // =================================================================

    /**
     * Resolve a hitscan ray against all alive zombies.
     * Standard ray-vs-AABB (slabs method). Returns the closest hit or null.
     * Does not apply damage — the GameScreen decides points/headshot bonus.
     *
     * Math (slabs method):
     *   For each axis, compute t where the ray enters/exits the slab
     *   [bbox.min, bbox.max]. The ray hits the box iff
     *   max(tminX, tminY, tminZ) <= min(tmaxX, tmaxY, tmaxZ) and that
     *   value is in front of the camera (>= 0).
     */
    public Zombie raycastZombie(Vector3 origin, Vector3 dir) {
        Zombie hit = null;
        float bestT = Float.MAX_VALUE;

        // Zombie half-extents (box centered on position; stand on floor)
        final float halfW = 0.4f;
        final float halfH = 0.9f;

        for (int i = 0; i < zombies.size; i++) {
            Zombie z = zombies.get(i);
            if (!z.alive) continue;

            // AABB for this zombie
            float minX = z.position.x - halfW, maxX = z.position.x + halfW;
            float minY = z.position.y,          maxY = z.position.y + 2f * halfH; // height ~1.8
            float minZ = z.position.z - halfW, maxZ = z.position.z + halfW;

            float tmin = 0f, tmax = Float.MAX_VALUE;

            // X slab
            if (Math.abs(dir.x) < 1e-6f) {
                if (origin.x < minX || origin.x > maxX) continue;
            } else {
                float t1 = (minX - origin.x) / dir.x;
                float t2 = (maxX - origin.x) / dir.x;
                if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) continue;
            }
            // Y slab
            if (Math.abs(dir.y) < 1e-6f) {
                if (origin.y < minY || origin.y > maxY) continue;
            } else {
                float t1 = (minY - origin.y) / dir.y;
                float t2 = (maxY - origin.y) / dir.y;
                if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) continue;
            }
            // Z slab
            if (Math.abs(dir.z) < 1e-6f) {
                if (origin.z < minZ || origin.z > maxZ) continue;
            } else {
                float t1 = (minZ - origin.z) / dir.z;
                float t2 = (maxZ - origin.z) / dir.z;
                if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) continue;
            }

            // tmin is the entry distance. Negative means we are inside the slab,
            // i.e. closest face is behind us — accept tmin clipped to >= 0.
            float t = Math.max(0f, tmin);
            if (t < bestT) {
                bestT = t;
                hit = z;
            }
        }
        return hit;
    }

    /** Apply damage to a zombie from a hit; returns true if the hit killed it. */
    public boolean damageZombie(Zombie z, float dmg) {
        if (z == null || !z.alive) return false;
        z.health -= dmg;
        z.hitFlash = 1f;
        return z.health <= 0f;
    }

    // ── Scratch vector (reused inside updateAI to avoid per-frame allocs) ──

    // =================================================================
    //  ACCESSORS / RESET
    // =================================================================
    public Array<Zombie> zombies() { return zombies; }
    public int getRound()          { return round; }
    public int getAliveCount()     { return aliveCount; }
    public int getToSpawn()        { return toSpawn; }
    public float getInterlude()    { return roundInterlude; }

    public void reset() {
        for (Zombie z : zombies) {
            z.alive = false;
            pool.free(z);
        }
        zombies.clear();
        round = 0;
        toSpawn = 0;
        aliveCount = 0;
        spawnTimer = 0f;
        roundInterlude = 0f;
    }
}
