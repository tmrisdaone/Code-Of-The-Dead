package com.survivalz.core;

import com.survivalz.core.entity.Player;
import com.survivalz.core.entity.PowerUp;
import com.survivalz.core.entity.Zombie;
import com.survivalz.core.entity.ZombieStates;
import com.survivalz.core.interact.Interactable;
import com.survivalz.core.pool.ObjectPool;
import com.survivalz.core.round.RoundManager;
import com.survivalz.core.config.BalanceConfig;
import com.survivalz.core.weapon.WeaponSystem;

import java.util.ArrayList;

/**
 * Central game model — holds all entities and subsystems.
 * Single entry point called by GameLoop at 60 Hz.
 */
public class GameWorld {

    private final Player player = new Player();
    private final RoundManager roundManager = new RoundManager();
    private final WeaponSystem weaponSystem = new WeaponSystem();
    private final ObjectPool<Zombie> zombiePool;

    private final ArrayList<Zombie> zombies = new ArrayList<>(32);
    private final ArrayList<Interactable> interactables = new ArrayList<>();
    private final ArrayList<PowerUp> powerups = new ArrayList<>();

    private Interactable hoveredInteractable = null;
    private boolean gameOver = false;

    // Input state
    private float moveX, moveY;
    private boolean firing;
    private boolean interactPressed;
    private boolean reloadPressed;

    // Spawn points for zombies
    private static final float[][] SPAWN_NODES = {
            { -10f, -10f }, { -10f, 10f }, { 10f, -10f }, { 10f, 10f },
            { -8f, -12f },  { 8f, -12f },  { -12f, 8f },  { 12f, 8f },
            { -15f, 0f },   { 15f, 0f },   { 0f, -15f },  { 0f, 15f }
    };

    public GameWorld() {
        zombiePool = new ObjectPool<Zombie>(8, BalanceConfig.ZOMBIE_MAX_POOL) {
            @Override protected Zombie newObject() { return new Zombie(); }
        };

        roundManager.addListener(new RoundManager.RoundListener() {
            @Override
            public void onRoundStarted(int round) {
                // UI layer listens via EventBus
                EventBus.INSTANCE.post(GameEvent.of(GameEvent.Type.ROUND_STARTED, round));
            }

            @Override
            public void onRoundEnded(int round) {
                EventBus.INSTANCE.post(GameEvent.of(GameEvent.Type.ROUND_ENDED, round));
            }
        });

        // Register spawn points from the map edge nodes
        for (float[] node : SPAWN_NODES) {
            roundManager.addSpawnPoint(node[0], node[1]);
        }

        // Start the first round
        roundManager.signalNextRound();
    }

    /** Called every frame by the game loop. */
    public void update(float deltaTime) {
        if (gameOver) return;

        // 1. Player
        player.update(deltaTime, moveX, moveY, firing);

        // 2. Weapon system
        boolean fired = weaponSystem.update(deltaTime, firing, reloadPressed);

        // 3. Handle firing: raycast damage
        if (fired) {
            performWeaponHit();
        }

        // 4. Round spawning (injects new zombies via callback)
        roundManager.update(deltaTime, this::spawnZombie);

        // 5. Zombies (backwards iteration for safe removal)
        for (int i = zombies.size() - 1; i >= 0; i--) {
            Zombie z = zombies.get(i);
            if (!z.isActive()) continue;

            z.update(deltaTime, player);

            if (z.isDead()) {
                // Award kill points (hit points already awarded in performWeaponHit)
                int pts = player.hasBuff(Player.Buff.DOUBLE_POINTS) ? 200 : 100;
                player.addPoints(pts);
                player.incrementKills(false);
                roundManager.onZombieKilled();

                // Chance to drop powerup
                if (Math.random() < 0.05f) {
                    PowerUp.Type[] types = PowerUp.Type.values();
                    powerups.add(new PowerUp(
                            z.getPosition().x, z.getPosition().y,
                            types[(int)(Math.random() * types.length)]
                    ));
                }

                // Reclaim
                zombies.remove(i);
                zombiePool.free(z);
            }
        }

        // 6. Scan for nearest interactable
        hoveredInteractable = null;
        float best = BalanceConfig.INTERACT_RADIUS * BalanceConfig.INTERACT_RADIUS;
        for (int i = 0, n = interactables.size(); i < n; i++) {
            Interactable in = interactables.get(i);
            if (in.canInteract(player)) {
                float d2 = player.getPosition().dist2(in.getX(), in.getY());
                if (d2 < best) {
                    best = d2;
                    hoveredInteractable = in;
                }
            }
        }
        if (interactPressed && hoveredInteractable != null) {
            hoveredInteractable.onInteract(player);
        }

        // 7. Powerups
        for (int i = powerups.size() - 1; i >= 0; i--) {
            PowerUp pu = powerups.get(i);
            pu.update(deltaTime);
            if (pu.isExpired()) {
                powerups.remove(i);
                continue;
            }
            if (player.getPosition().dist2(pu.getPosition().x, pu.getPosition().y) < 0.5f) {
                pu.apply(player);
                EventBus.INSTANCE.post(GameEvent.of(GameEvent.Type.POWERUP_COLLECTED, pu.getType()));
                powerups.remove(i);
            }
        }

        // 8. Check game over
        if (!player.isAlive()) {
            gameOver = true;
            EventBus.INSTANCE.post(GameEvent.of(GameEvent.Type.GAME_OVER));
        }

        // Reset per-frame input
        moveX = 0f;
        moveY = 0f;
        interactPressed = false;
        reloadPressed = false;
    }

    private void spawnZombie(float x, float y, int round,
                             float hMult, float dMult, float sMult) {
        Zombie z = zombiePool.obtain();
        z.spawn(x, y, round, hMult, dMult, sMult);
        zombies.add(z);
    }

    private void performWeaponHit() {
        var wep = weaponSystem.getActiveWeapon();
        if (wep == null) return;

        float damage = wep.getDamage();
        float aimDirX = player.getAimDirX();
        float aimDirY = player.getAimDirY();

        // Find closest active zombie in front of the player
        Zombie target = null;
        float bestD2 = Float.MAX_VALUE;

        for (int i = 0; i < zombies.size(); i++) {
            Zombie z = zombies.get(i);
            if (!z.isActive()) continue;

            float dx = z.getPosition().x - player.getPosition().x;
            float dy = z.getPosition().y - player.getPosition().y;
            float d2 = dx * dx + dy * dy;

            // Must be within 20 world units
            if (d2 > 400f) continue;

            // Check if zombie is roughly in front of player (dot product)
            float dist = (float) Math.sqrt(d2);
            float dot = (dx / dist) * aimDirX + (dy / dist) * aimDirY;
            if (dot < 0.5f) continue; // not in the 120° cone in front

            if (d2 < bestD2) {
                bestD2 = d2;
                target = z;
            }
        }

        if (target == null) return;

        // Insta-kill only affects the targeted zombie
        boolean wasKill = false;
        if (player.hasBuff(Player.Buff.INSTAKILL)) {
            target.takeDamage(target.getHealth()); // kills instantly
            wasKill = true;
        } else {
            target.takeDamage((int) damage);
            if (target.isDead()) {
                wasKill = true;
            } else {
                player.addPoints(BalanceConfig.POINTS_PER_HIT);
            }
        }

        if (wasKill) {
            // Points are awarded in the zombie death detection loop below.
            // Mark killed by hit so the death loop doesn't double-award.
        }
    }

    // ── Input ────────────────────────────────────────────────

    public void setMoveInput(float x, float y) { this.moveX = x; this.moveY = y; }
    public void setFiring(boolean firing) { this.firing = firing; }
    public void setInteractPressed() { this.interactPressed = true; }
    public void setReloadPressed() { this.reloadPressed = true; }
    public void setWeaponSwitch() { weaponSystem.switchWeapon(); }

    // ── Registration ─────────────────────────────────────────

    public void addInteractable(Interactable in) { interactables.add(in); }
    public void addPowerUp(float x, float y, PowerUp.Type type) {
        powerups.add(new PowerUp(x, y, type));
    }

    // ── Reset ────────────────────────────────────────────────

    public void reset() {
        player.reset();
        weaponSystem.reset();
        roundManager.reset();
        zombies.clear();
        powerups.clear();
        interactables.clear();
        hoveredInteractable = null;
        gameOver = false;
        roundManager.signalNextRound();
        EventBus.INSTANCE.post(GameEvent.of(GameEvent.Type.GAME_RESET));
    }

    // ── Accessors ────────────────────────────────────────────

    public Player getPlayer() { return player; }
    public ArrayList<Zombie> getZombies() { return zombies; }
    public ArrayList<PowerUp> getPowerups() { return powerups; }
    public Interactable getHoveredInteractable() { return hoveredInteractable; }
    public RoundManager getRoundManager() { return roundManager; }
    public WeaponSystem getWeaponSystem() { return weaponSystem; }
    public boolean isGameOver() { return gameOver; }
}
