package com.survivalz.core;

public class Zombie {
    // Transforms (package-visible for hot-path state logic)
    final Vector2 position = new Vector2();
    float speed = 2.0f;
    float radius = 0.35f;

    // Vitals
    int maxHealth;
    int health;
    int attackDamage;
    float attackCooldown = 1.0f;
    float attackTimer = 0f;

    // Lifecycle
    private ZombieState currentState;
    boolean active = false;

    // Target cache refilled each frame by GameWorld
    Player target;

    public void spawn(float sx, float sy, int round,
                      float healthMult, float dmgMult, float speedMult) {
        position.set(sx, sy);
        maxHealth = (int)(150 * healthMult);
        health = maxHealth;
        attackDamage = (int)(20 * dmgMult);
        speed = 2.0f * speedMult;
        active = true;
        attackTimer = 0.5f;
        transitionState(ZombieStates.CHASE);
    }

    public void update(float deltaTime, Player player) {
        if (!active) return;
        this.target = player;
        if (attackTimer > 0f) attackTimer -= deltaTime;
        currentState.update(this, deltaTime);
    }

    public void takeDamage(int amount) {
        health -= amount;
        if (health <= 0 && currentState != ZombieStates.DEAD) {
            transitionState(ZombieStates.DEAD);
        }
    }

    public void transitionState(ZombieState next) {
        if (currentState != null) currentState.exit(this);
        currentState = next;
        currentState.enter(this);
    }

    public ZombieState getState() { return currentState; }
    public boolean isActive() { return active; }
    public Vector2 getPosition() { return position; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
}
