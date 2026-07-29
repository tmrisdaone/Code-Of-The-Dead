package com.survivalz.core.round;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages round lifecycle: grace period, spawning, active, and completion.
 * Zombie count, health, damage, and speed scale with round number.
 */
public class RoundManager {
    public enum Phase { GRACE, SPAWNING, ACTIVE, COMPLETE }

    private int round = 0;
    private Phase phase = Phase.GRACE;
    private int zombiesToSpawn;
    private int zombiesAlive;
    private float spawnTimer;
    private float graceTimer;

    private final List<RoundListener> listeners = new ArrayList<>();
    private final List<SpawnPoint> spawnPoints = new ArrayList<>();

    // Scalars forwarded to each spawned zombie
    private float healthMult = 1f;
    private float damageMult = 1f;
    private float speedMult = 1f;

    public void setSpawnPoints(List<SpawnPoint> pts) {
        spawnPoints.clear();
        spawnPoints.addAll(pts);
    }

    public void addSpawnPoint(float x, float y) {
        spawnPoints.add(new SpawnPoint(x, y));
    }

    public void update(float deltaTime, Spawner callback) {
        switch (phase) {
            case GRACE:
                graceTimer -= deltaTime;
                if (graceTimer <= 0f) startNextRound();
                break;

            case SPAWNING:
            case ACTIVE:
                if (zombiesToSpawn > 0) {
                    spawnTimer -= deltaTime;
                    if (spawnTimer <= 0f) {
                        spawnOne(callback);
                        zombiesToSpawn--;
                        zombiesAlive++;
                        spawnTimer = getSpawnDelay(round);
                    }
                }
                if (zombiesToSpawn == 0 && zombiesAlive == 0) {
                    completeRound();
                }
                break;

            case COMPLETE:
                break;
        }
    }

    private void startNextRound() {
        round++;
        phase = Phase.SPAWNING;
        zombiesToSpawn = calculateZombieCount(round);
        zombiesAlive = 0;
        spawnTimer = 0f;

        if (round <= 9) {
            healthMult = round;
            damageMult = 1f;
            speedMult = 1f + (round - 1) * 0.05f;
        } else {
            healthMult = 9f * (float) Math.pow(1.1f, round - 9);
            damageMult = 1f + (round - 10) * 0.02f;
            speedMult = 1.4f;
        }

        for (RoundListener l : listeners) l.onRoundStarted(round);
    }

    private int calculateZombieCount(int r) {
        if (r == 1) return 6;
        if (r == 2) return 8;
        if (r == 3) return 10;
        if (r == 4) return 12;
        if (r == 5) return 14;
        int count = 18 + (r - 5) * 2;
        return Math.min(count, 24); // cap at 24 for mobile thermal headroom
    }

    private float getSpawnDelay(int r) {
        return Math.max(0.25f, 2.0f - r * 0.08f);
    }

    private void spawnOne(Spawner cb) {
        if (spawnPoints.isEmpty()) return;
        SpawnPoint pt = spawnPoints.get((int) (Math.random() * spawnPoints.size()));
        cb.spawn(pt.x, pt.y, round, healthMult, damageMult, speedMult);
    }

    public void onZombieKilled() {
        zombiesAlive--;
    }

    private void completeRound() {
        phase = Phase.GRACE;
        graceTimer = 5f; // 5 second breather
        for (RoundListener l : listeners) l.onRoundEnded(round);
    }

    public void signalNextRound() {
        if (phase == Phase.GRACE) {
            graceTimer = 0f; // skip grace
        }
    }

    public void addListener(RoundListener l) {
        listeners.add(l);
    }

    public int getRound() { return round; }
    public Phase getPhase() { return phase; }
    public int getZombiesAlive() { return zombiesAlive; }
    public int getZombiesToSpawn() { return zombiesToSpawn; }
    public float getHealthMult() { return healthMult; }
    public float getDamageMult() { return damageMult; }
    public float getSpeedMult() { return speedMult; }

    public void reset() {
        round = 0;
        phase = Phase.GRACE;
        zombiesToSpawn = 0;
        zombiesAlive = 0;
        spawnTimer = 0f;
        graceTimer = 0f;
    }

    // ── Callbacks & Data ─────────────────────────────────────

    public interface RoundListener {
        void onRoundStarted(int round);
        void onRoundEnded(int round);
    }

    public interface Spawner {
        void spawn(float x, float y, int round, float h, float d, float s);
    }

    public static class SpawnPoint {
        public final float x;
        public final float y;
        public SpawnPoint(float x, float y) { this.x = x; this.y = y; }
    }
}
