package com.survivalz.core.entity;

import com.survivalz.core.math.Vec2;

/**
 * Individual zombie entity with an enum-based state machine.
 * Pre-allocated via ObjectPool — never use new in hot loops.
 */
public class Zombie {
    // Position (package-visible for hot-path state logic)
    final Vec2 position = new Vec2();
    float speed = 2.0f;

    // Vitals
    int maxHealth;
    int health;
    int attackDamage;
    float attackCooldown = 1.0f;
    float attackTimer = 0f;

    // Lifecycle
    private ZombieState currentState;
    boolean active = false;

    // Target refilled each frame by GameWorld
    Player target;

    // Visual
    public float hitFlashTimer = 0f;
    public float bobPhase = 0f;

    public Zombie() {}

    public void spawn(float sx, float sy, int round,
                      float healthMult, float dmgMult, float speedMult) {
        position.set(sx, sy);
        maxHealth = (int) (150 * healthMult);
        health = maxHealth;
        attackDamage = (int) (20 * dmgMult);
        speed = 2.0f * speedMult;
        active = true;
        attackTimer = 0.5f;
        hitFlashTimer = 0f;
        bobPhase = 0f;
        transitionState(ZombieStates.CHASE);
    }

    public void update(float deltaTime, Player player) {
        if (!active) return;
        this.target = player;
        if (attackTimer > 0f) attackTimer -= deltaTime;
        if (hitFlashTimer > 0f) hitFlashTimer -= deltaTime;
        currentState.update(this, deltaTime);
        bobPhase += deltaTime * 4f;
    }

    public void takeDamage(int amount) {
        hitFlashTimer = 0.1f;
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

    public Vec2 getPosition() { return position; }
    public ZombieState getState() { return currentState; }
    public boolean isActive() { return active; }
    public boolean isDead() { return currentState == ZombieStates.DEAD; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public float getHitFlashTimer() { return hitFlashTimer; }

    /** Distance squared to a 2D point. */
    public float dist2To(float x, float y) {
        return position.dist2(x, y);
    }
}
