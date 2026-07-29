package com.survivalz.core;

import java.util.ArrayList;
import java.util.List;

public class RoundManager {
    public enum Phase { GRACE, SPAWNING, ACTIVE, COMPLETE }

    public interface RoundListener {
        void onRoundStarted(int round);
        void onRoundEnded(int round);
    }

    public interface Spawner {
        /** Called when the round manager wants a new zombie spawned at (x, y). */
        void spawn(float x, float y, int round, float healthMult, float dmgMult, float speedMult);
    }

    public interface Killer {
        /** Called when the round manager wants all live zombies killed (e.g. NUKE). */
        void killAll();
    }

    public static final class SpawnPoint {
        public final float x, y;
        public SpawnPoint(float x, float y) { this.x = x; this.y = y; }
    }

    private int round = 0;
    private Phase phase = Phase.GRACE;
    private int zombiesToSpawn;
    private int zombiesAlive;
    private float spawnTimer;
    private float graceTimer = 5f; // initial grace before round 1

    private final List<RoundListener> listeners = new ArrayList<>();
    private final List<SpawnPoint> spawnPoints = new ArrayList<>();

    // Scalars forwarded to each spawned zombie
    private float healthMult = 1f;
    private float damageMult = 1f;
    private float speedMult = 1f;

    // Optional killer; used by NUKE power-up via killAllActiveZombies().
    private Killer killer;

    public void setSpawnPoints(List<SpawnPoint> pts) {
        spawnPoints.clear();
        spawnPoints.addAll(pts);
    }

    public void setKiller(Killer killer) { this.killer = killer; }

    public void addListener(RoundListener l) { listeners.add(l); }

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
                // Immediately transitions back to GRACE inside completeRound()
                break;
        }
    }

    private void startNextRound() {
        round++;
        phase = Phase.SPAWNING;
        zombiesToSpawn = calculateZombieCount(round);
        zombiesAlive = 0;
        spawnTimer = 0f;

        // Difficulty curve: ~10% health, ~5% damage/speed per round.
        healthMult = 1f + (round - 1) * 0.10f;
        damageMult = 1f + (round - 1) * 0.05f;
        speedMult  = 1f + (round - 1) * 0.05f;

        for (RoundListener l : listeners) l.onRoundStarted(round);
    }

    private void spawnOne(Spawner callback) {
        if (spawnPoints.isEmpty()) return;
        SpawnPoint sp = spawnPoints.get((int)(Math.random() * spawnPoints.size()));
        callback.spawn(sp.x, sp.y, round, healthMult, damageMult, speedMult);
    }

    /** Called by GameWorld whenever a zombie dies so the manager can close the round. */
    public void onZombieKilled() {
        if (zombiesAlive > 0) zombiesAlive--;
    }

    private void completeRound() {
        phase = Phase.COMPLETE;
        graceTimer = 8f; // breather between rounds
        phase = Phase.GRACE;
        for (RoundListener l : listeners) l.onRoundEnded(round);
    }

    /** NUKE power-up hook: ask the owning world to kill every active zombie. */
    public void killAllActiveZombies() {
        if (killer != null) killer.killAll();
    }

    private int calculateZombieCount(int r) {
        // Classic-style scaling: 6 on round 1, +3 per round up to a cap.
        return Math.min(6 + (r - 1) * 3, 24);
    }

    private float getSpawnDelay(int r) {
        // Spawns get slightly faster as rounds climb, floored at 0.4 s.
        return Math.max(0.4f, 1.2f - (r - 1) * 0.05f);
    }

    public int getRound() { return round; }
    public Phase getPhase() { return phase; }
    public int getZombiesAlive() { return zombiesAlive; }
    public int getZombiesToSpawn() { return zombiesToSpawn; }
}
