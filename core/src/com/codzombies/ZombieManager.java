package com.codzombies;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * Manages all zombie entities: wave spawning, AI update, hit detection.
 * Uses ObjectPool<Zombie> — zero allocations in hot paths.
 */
public class ZombieManager {

    private final ObjectPool<Zombie> zombiePool;
    private final ObjectPool<Bullet> bulletPool;

    // ── Wave state ───────────────────────────────────────────
    public  int     currentRound = 0;
    private int     zombiesSpawnedThisRound = 0;
    private int     zombiesToSpawnThisRound = 0;
    private float   spawnTimer  = 0f;
    private float   interSpawnDelay = 1.5f;
    private boolean roundActive = false;
    private boolean roundTransition = false;
    public  float   roundTransitionTimer = 0f;

    // ── Spawn nodes (pre-defined positions around map edge) ──
    private static final float[][] SPAWN_NODES = {
            { -10f, -10f }, { -10f, 10f }, { 10f, -10f }, { 10f, 10f },
            { -8f, -12f },  { 8f, -12f },  { -12f, 8f },  { 12f, 8f },
            { -15f, 0f },   { 15f, 0f },   { 0f, -15f },  { 0f, 15f }
    };

    // ── Player reference ────────────────────────────────────
    private final PlayerController player;

    // ── Temp vectors (no alloc) ──────────────────────────────
    private final Vector3 tmpDir  = new Vector3();
    private final Vector3 tmpPos  = new Vector3();
    private final Vector3 tmpHit  = new Vector3();

    // ── Hit result (reused) ──────────────────────────────────
    public static class HitResult {
        public boolean hit;
        public boolean headshot;
        public int     zombieIndex;
        public float   damage;
        public float   distance;
    }

    private final HitResult reusableHit = new HitResult();

    // ── Internal zombie count tracking ───────────────────────
    public int aliveCount = 0;
    public int totalKills = 0;

    public ZombieManager(PlayerController player) {
        this.player = player;
        this.zombiePool = new ObjectPool<>(Constants.ZOMBIE_MAX_POOL, Zombie::new);
        this.bulletPool = new ObjectPool<>(32, Bullet::new);
    }

    // ── Main update ──────────────────────────────────────────
    public void update(float dt) {
        if (dt > Constants.DELTA_MAX) dt = Constants.DELTA_MAX;

        // Round transition countdown
        if (roundTransition) {
            roundTransitionTimer -= dt;
            if (roundTransitionTimer <= 0f) {
                roundTransition = false;
                startRound();
            }
            return; // pause all zombie activity during transition
        }

        // Spawn logic
        if (roundActive && zombiesSpawnedThisRound < zombiesToSpawnThisRound) {
            spawnTimer -= dt;
            if (spawnTimer <= 0f) {
                spawnZombie();
                // Spawn faster as rounds progress
                interSpawnDelay = Math.max(0.3f, 1.5f - currentRound * 0.02f);
                spawnTimer = interSpawnDelay;
            }
        }

        // Update all zombies
        aliveCount = 0;
        zombiePool.forEachActive((zombie, index) -> {
            if (zombie.isDead()) {
                zombie.deathTimer -= dt;
                if (zombie.deathTimer <= 0f) {
                    zombiePool.freeIndex(index);
                }
                return;
            }

            // Hit flash timer
            if (zombie.hitFlashTimer > 0f) zombie.hitFlashTimer -= dt;

            switch (zombie.state) {
                case Zombie.STATE_SPAWNING:
                    zombie.spawnTimer -= dt;
                    if (zombie.spawnTimer <= 0f) {
                        zombie.state = Zombie.STATE_PURSUING;
                    }
                    break;

                case Zombie.STATE_PURSUING:
                    updatePursuit(zombie, dt);
                    break;

                case Zombie.STATE_ATTACKING:
                    updateAttack(zombie, dt);
                    break;
            }

            aliveCount++;
        });

        // Update bullets
        bulletPool.forEachActive((bullet, index) -> {
            bullet.lifetime -= dt;
            if (bullet.lifetime <= 0f) {
                bulletPool.freeIndex(index);
                return;
            }
            bullet.position.x += bullet.direction.x * bullet.speed * dt;
            bullet.position.y += bullet.direction.y * bullet.speed * dt;
            bullet.position.z += bullet.direction.z * bullet.speed * dt;

            // Splash damage check
            if (bullet.splashRadius > 0f) {
                zombiePool.forEachActive((zom, zi) -> {
                    if (!zom.isActive()) return;
                    float d2 = zom.dist2To(bullet.position.x, bullet.position.z);
                    if (d2 <= bullet.splashRadius * bullet.splashRadius) {
                        zom.health -= bullet.damage * 0.5f;
                        zom.hitFlashTimer = 0.1f;
                        if (zom.health <= 0f) {
                            zom.kill();
                            player.addPoints(Constants.POINTS_PER_KILL);
                            player.zombieKills++;
                            totalKills++;
                        }
                    }
                });
                bulletPool.freeIndex(index);
            }
        });

        // Check round complete
        if (roundActive && aliveCount == 0 &&
                zombiesSpawnedThisRound >= zombiesToSpawnThisRound) {
            roundActive = false;
            // Automatically start next round after delay
            signalNextRound();
        }
    }

    // ── Round management ─────────────────────────────────────

    public void signalNextRound() {
        if (roundActive || roundTransition) return;
        currentRound++;
        zombiesToSpawnThisRound = Constants.zombieCountForRound(currentRound);
        zombiesSpawnedThisRound = 0;
        roundTransition = true;
        roundTransitionTimer = 3.0f; // "ROUND X" display duration
    }

    public void startRound() {
        roundActive = true;
        spawnTimer = 0f; // spawn first zombie immediately
    }

    public boolean isRoundActive() {
        return roundActive;
    }

    public boolean isRoundTransition() {
        return roundTransition;
    }

    // ── Hit-scan ─────────────────────────────────────────────

    /**
     * Raycast from camera to find closest zombie hit.
     * Returns reusable HitResult (valid only until next call).
     */
    public HitResult raycastHit(Vector3 origin, Vector3 direction,
                                 float maxRange, float headshotHeight) {
        reusableHit.hit = false;
        reusableHit.headshot = false;
        reusableHit.zombieIndex = -1;
        reusableHit.damage = 0f;
        reusableHit.distance = maxRange;

        zombiePool.forEachActive((zombie, index) -> {
            if (!zombie.isActive()) return;

            // Simple bounding-sphere intersection
            // Body: radius 0.4 units at zombie position
            // Head: radius 0.2 units at zombie position + headshotHeight Y
            tmpPos.set(zombie.position);

            // Body check
            float dist2 = tmpPos.dst2(origin);
            if (dist2 > maxRange * maxRange) return;

            tmpDir.set(tmpPos).sub(origin).nor();
            float dot = tmpDir.dot(direction);
            if (dot < 0.85f) return; // not in crosshair cone

            float distance = origin.dst(tmpPos);
            if (distance < reusableHit.distance && distance < maxRange) {
                reusableHit.hit = true;
                reusableHit.zombieIndex = index;
                reusableHit.distance = distance;

                // Headshot check: if aim is above body center
                float aimHeight = origin.y - tmpPos.y;
                reusableHit.headshot = (aimHeight > headshotHeight);
                reusableHit.damage = reusableHit.headshot ? 2.0f : 1.0f;

                // Apply damage immediately
                Weapon wep = player.weaponSystem != null
                        ? player.weaponSystem.getActiveWeapon() : null;
                float dmg = (wep != null) ? wep.damage : 25f;
                if (reusableHit.headshot) dmg *= 2.0f;

                zombie.health -= dmg;
                zombie.hitFlashTimer = 0.1f;

                if (zombie.health <= 0f) {
                    zombie.kill();
                    player.addPoints(reusableHit.headshot
                            ? Constants.POINTS_PER_HEADSHOT
                            : Constants.POINTS_PER_KILL);
                    if (reusableHit.headshot) player.headshots++;
                    player.zombieKills++;
                    totalKills++;
                }
            }
        });

        return reusableHit.hit ? reusableHit : null;
    }

    // ── Bullet spawn ─────────────────────────────────────────
    public boolean spawnBullet(float px, float py, float pz,
                                float dx, float dy, float dz,
                                float speed, float damage,
                                float splashRadius, float lifetime) {
        Bullet b = bulletPool.obtain();
        if (b == null) return false;
        b.set(px, py, pz, dx, dy, dz, speed, damage, splashRadius, lifetime);
        return true;
    }

    // ── Zombie accessors ─────────────────────────────────────
    public ObjectPool<Zombie> getZombiePool() { return zombiePool; }

    public int getZombieCount() { return aliveCount; }
    public int getCurrentRound() { return currentRound; }

    public void reset() {
        // Free all zombies
        for (int i = 0; i < zombiePool.capacity(); i++) {
            zombiePool.freeIndex(i);
        }
        for (int i = 0; i < bulletPool.capacity(); i++) {
            bulletPool.freeIndex(i);
        }
        currentRound = 0;
        zombiesSpawnedThisRound = 0;
        zombiesToSpawnThisRound = 0;
        roundActive = false;
        roundTransition = false;
        roundTransitionTimer = 0f;
        aliveCount = 0;
        totalKills = 0;
    }

    // ── Private ──────────────────────────────────────────────

    private void spawnZombie() {
        Zombie z = zombiePool.obtain();
        if (z == null) return; // pool full — wait for deaths

        // Pick a random spawn node that's far enough from player
        float px = player.position.x;
        float pz = player.position.z;
        float sx, sz;
        int attempts = 0;
        do {
            int idx = (int)(Math.random() * SPAWN_NODES.length);
            sx = SPAWN_NODES[idx][0];
            sz = SPAWN_NODES[idx][1];
            attempts++;
        } while (attempts < 10 && (sx - px) * (sx - px) + (sz - pz) * (sz - pz) < 25f);

        float health = Constants.zombieHealthForRound(currentRound);
        float speed  = Constants.ZOMBIE_SPEED + currentRound * 0.02f; // slightly faster each round
        z.init(sx, sz, health, speed);
        zombiesSpawnedThisRound++;
    }

    private void updatePursuit(Zombie zombie, float dt) {
        // Vector toward player
        float dx = player.position.x - zombie.position.x;
        float dz = player.position.z - zombie.position.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.01f) return;

        // Move toward player
        zombie.velocity.x = (dx / dist) * zombie.speed;
        zombie.velocity.z = (dz / dist) * zombie.speed;

        zombie.position.x += zombie.velocity.x * dt;
        zombie.position.z += zombie.velocity.z * dt;

        // Animation
        zombie.bobPhase += dt * 4f;

        // Check attack range
        if (dist < Constants.ZOMBIE_ATTACK_RANGE) {
            zombie.state = Zombie.STATE_ATTACKING;
            zombie.attackCooldown = 0f;
        }
    }

    private void updateAttack(Zombie zombie, float dt) {
        zombie.attackCooldown -= dt;

        // Check if player moved out of range
        float dx = player.position.x - zombie.position.x;
        float dz = player.position.z - zombie.position.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);

        if (dist > Constants.ZOMBIE_ATTACK_RANGE * 1.2f) {
            zombie.state = Zombie.STATE_PURSUING;
            return;
        }

        // Attack on cooldown
        if (zombie.attackCooldown <= 0f) {
            player.takeDamage(zombie.damagePerHit);
            zombie.attackCooldown = Constants.ZOMBIE_ATTACK_COOLDOWN;
        }
    }
}
