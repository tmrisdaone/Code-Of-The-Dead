package com.survivalz.core;

import java.util.ArrayList;
import java.util.Arrays;

public class GameWorld implements RoundManager.Spawner, RoundManager.Killer {
    private final Player player = new Player();
    private final RoundManager roundManager = new RoundManager();
    private final ObjectPool<Zombie> zombiePool;

    private final ArrayList<Zombie> zombies = new ArrayList<>(32);
    private final ArrayList<Interactable> interactables = new ArrayList<>();
    private final ArrayList<PowerUp> powerups = new ArrayList<>();

    private Interactable hoveredInteractable = null;

    public GameWorld() {
        // Pre-warm pool: allocate 8 up front, max 32 in memory
        zombiePool = new ObjectPool<Zombie>(8, 32) {
            @Override protected Zombie newObject() { return new Zombie(); }
        };

        roundManager.addListener(new RoundManager.RoundListener() {
            @Override public void onRoundStarted(int round) {
                // Trigger UI flash / audio cue
            }
            @Override public void onRoundEnded(int round) {
                // Chance to drop a PowerUp
            }
        });

        roundManager.setSpawnPoints(Arrays.asList(
            new RoundManager.SpawnPoint(10f, 10f),
            new RoundManager.SpawnPoint(22f, 8f)
        ));

        // Wire NUKE support: round manager delegates kills back to this world.
        roundManager.setKiller(this);

        // Populate the map's interactables: doors, wall-buys, and the mystery box.
        registerMapInteractables();
    }

    /** Lays out the doors, wall-buys and mystery box for the default map. */
    private void registerMapInteractables() {
        // Doors unlock new zones.
        interactables.add(new DoorBuy( 5f,  0f, 750,  "zone_a"));
        interactables.add(new DoorBuy(-5f,  0f, 1000, "zone_b"));
        // Wall-buys: buy the weapon once, then refill ammo.
        interactables.add(new WallBuy( 3f,  3f, "m14",  500, 250));
        interactables.add(new WallBuy(-3f, -3f, "mp40", 1000, 500));
        interactables.add(new WallBuy( 6f, -2f, "stg44", 1500, 750));
        // Mystery box: random weapon from loot table for 950 pts.
        interactables.add(new MysteryBox(0f, 6f, java.util.Arrays.asList(
            "m14", "mp40", "stg44", "raygun"
        )));
    }

    /** Single entry point called by the game loop at 60 Hz. */
    public void update(float deltaTime, InputState input) {
        // 1. Player
        player.update(deltaTime, input.moveX, input.moveY,
                      input.aimX, input.aimY, input.firing);

        // 1b. Fire: consume a shot (ammo + fire-rate gating lives in Player)
        //     and apply a hit-scan to the nearest zombie in the aim cone.
        if (input.firing) {
            Weapon fired = player.consumeShot();
            if (fired != null) {
                firingPlayerWeapon(fired);
            }
        }

        // 2. Round spawning (injects new zombies via callback)
        roundManager.update(deltaTime, this);

        // 3. Zombies (backwards iteration for safe removal)
        for (int i = zombies.size() - 1; i >= 0; i--) {
            Zombie z = zombies.get(i);
            if (!z.isActive()) continue;

            z.update(deltaTime, player);

            if (z.getState() == ZombieStates.DEAD) {
                // Award points
                int pts = player.hasBuff(Player.Buff.DOUBLE_POINTS) ? 200 : 100;
                player.addPoints(pts);
                roundManager.onZombieKilled();

                // Reclaim
                zombies.remove(i);
                zombiePool.free(z);
            }
        }

        // 4. Scan for nearest interactable
        hoveredInteractable = null;
        float best = Player.INTERACT_RADIUS * Player.INTERACT_RADIUS;
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
        if (input.interactPressed && hoveredInteractable != null) {
            hoveredInteractable.onInteract(player);
        }

        // 5. Powerups
        for (int i = powerups.size() - 1; i >= 0; i--) {
            PowerUp pu = powerups.get(i);
            pu.update(deltaTime);
            if (pu.isExpired()) {
                powerups.remove(i);
                continue;
            }
            if (player.getPosition().dist2(pu.getPosition().x, pu.getPosition().y) < 0.5f) {
                pu.apply(player, roundManager);
                powerups.remove(i);
            }
        }

        // 6. Tick interactables that need per-frame updates (e.g. MysteryBox)
        for (int i = 0, n = interactables.size(); i < n; i++) {
            Interactable in = interactables.get(i);
            if (in instanceof MysteryBox) {
                ((MysteryBox) in).update(deltaTime);
            }
        }
    }

    @Override
    public void spawn(float x, float y, int round,
                      float hMult, float dMult, float sMult) {
        Zombie z = zombiePool.obtain();
        z.spawn(x, y, round, hMult, dMult, sMult);
        zombies.add(z);
    }

    /** NUKE: kill every active zombie. Called by RoundManager via the Killer hook. */
    @Override
    public void killAll() {
        for (int i = zombies.size() - 1; i >= 0; i--) {
            Zombie z = zombies.get(i);
            if (z.isActive()) {
                z.takeDamage(z.health);
            }
        }
    }

    /**
     * Hit-scan a single shot from the player's current weapon: applies damage to
     * the nearest active zombie within {@code FIRE_RANGE} that lies inside the
     * aim cone, awarding points on hit/kill. Called once per consumed shot.
     */
    private void firingPlayerWeapon(Weapon fired) {
        final float FIRE_RANGE = 20f;            // world units
        final float FIRE_RANGE2 = FIRE_RANGE * FIRE_RANGE;
        final float MIN_DOT = 0.5f;               // ~60° half-cone in front of aim

        float aimX = (float) Math.cos(player.getAimAngle());
        float aimY = (float) Math.sin(player.getAimAngle());
        float px = player.getPosition().x;
        float py = player.getPosition().y;

        Zombie target = null;
        float bestD2 = Float.MAX_VALUE;
        for (int i = 0; i < zombies.size(); i++) {
            Zombie z = zombies.get(i);
            if (!z.isActive()) continue;
            float dx = z.position.x - px;
            float dy = z.position.y - py;
            float d2 = dx * dx + dy * dy;
            if (d2 > FIRE_RANGE2 || d2 >= bestD2) continue;
            float inv = 1f / (float) Math.sqrt(d2);
            if (dx * aimX * inv + dy * aimY * inv < MIN_DOT) continue;
            target = z;
            bestD2 = d2;
        }

        if (target == null) return;

        boolean headshot = Math.random() < 0.25;
        boolean wasAlive = target.isActive();
        damageZombie(target, fired.getDamage(), headshot);

        // Award points on the hit itself; kill points are awarded in the
        // death-reap loop (step 3) to keep the economy single-sourced.
        boolean nowDead = !target.isActive();
        if (wasAlive && !nowDead) {
            player.addPoints(headshot ? 100 : 10); // hit score; headshots pay more
        }
    }

    /** Called by bullet/raycast system when a shot lands. */
    public void damageZombie(Zombie z, int baseDmg, boolean headshot) {
        int dmg = headshot ? baseDmg * 2 : baseDmg;
        if (player.hasBuff(Player.Buff.INSTAKILL)) {
            dmg = z.health; // ignores round scaling
        }
        z.takeDamage(dmg);
    }

    // --- Registration helpers ---

    public void addInteractable(Interactable in) { interactables.add(in); }
    public void addPowerUp(float x, float y, PowerUp.Type type) {
        powerups.add(new PowerUp(x, y, type));
    }

    // --- Accessors for the Renderer / HUD ---

    public Player getPlayer() { return player; }
    public ArrayList<Zombie> getZombies() { return zombies; }
    public ArrayList<PowerUp> getPowerUps() { return powerups; }
    public ArrayList<Interactable> getInteractables() { return interactables; }
    public Interactable getHoveredInteractable() { return hoveredInteractable; }
    public RoundManager getRoundManager() { return roundManager; }

    /** Snapshot of frame inputs produced by the Android touch layer. */
    public static class InputState {
        public float moveX, moveY;
        public float aimX, aimY;
        public boolean firing;
        public boolean interactPressed;

        public void reset() {
            moveX = moveY = aimX = aimY = 0f;
            firing = false;
            interactPressed = false;
        }
    }
}
