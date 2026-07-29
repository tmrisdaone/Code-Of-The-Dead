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
}

/** Contract for all zombie behaviors. */
public interface ZombieState {
    void enter(Zombie z);
    void update(Zombie z, float deltaTime);
    void exit(Zombie z);
}

/** Zero-allocation state implementations. */
public enum ZombieStates implements ZombieState {
    CHASE {
        // Square of attack range to avoid sqrt every frame: $$0.8$$ m
        private static final float ATK_R2 = 0.8f * 0.8f;

        @Override
        public void update(Zombie z, float dt) {
            Vector2 p = z.target.getPosition();
            float dx = p.x - z.position.x;
            float dy = p.y - z.position.y;
            float dist2 = dx * dx + dy * dy;

            if (dist2 <= ATK_R2) {
                z.transitionState(ATTACK);
                return;
            }

            float len = (float) Math.sqrt(dist2);
            if (len > 0f) {
                z.position.x += (dx / len) * z.speed * dt;
                z.position.y += (dy / len) * z.speed * dt;
            }
        }
        @Override public void enter(Zombie z) {}
        @Override public void exit(Zombie z) {}
    },

    ATTACK {
        // Slightly larger than $$0.8$$ so the zombie does not flicker states
        private static final float BREAK_R2 = 0.9f * 0.9f;

        @Override
        public void update(Zombie z, float dt) {
            Vector2 p = z.target.getPosition();
            float dx = p.x - z.position.x;
            float dy = p.y - z.position.y;

            if (dx * dx + dy * dy > BREAK_R2) {
                z.transitionState(CHASE);
                return;
            }

            if (z.attackTimer <= 0f) {
                z.target.applyDamage(z.attackDamage);
                z.attackTimer = z.attackCooldown;
            }
        }
        @Override public void enter(Zombie z) { z.attackTimer = 0.25f; } // quick first swing
        @Override public void exit(Zombie z) {}
    },

    DEAD {
        @Override
        public void enter(Zombie z) {
            z.active = false;
            // GameWorld detects this state and reclaims the zombie into the pool.
        }
        @Override public void update(Zombie z, float dt) {}
        @Override public void exit(Zombie z) {}
    }
}
