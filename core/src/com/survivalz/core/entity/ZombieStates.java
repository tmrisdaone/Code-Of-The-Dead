package com.survivalz.core.entity;

/**
 * Zero-allocation state implementations for the Zombie state machine.
 */
public enum ZombieStates implements ZombieState {
    CHASE {
        private static final float ATK_R2 = 0.8f * 0.8f;

        @Override
        public void update(Zombie z, float dt) {
            if (z.target == null) return;
            float dx = z.target.getPosition().x - z.position.x;
            float dy = z.target.getPosition().y - z.position.y;
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
        private static final float BREAK_R2 = 0.9f * 0.9f;

        @Override
        public void update(Zombie z, float dt) {
            if (z.target == null) return;
            float dx = z.target.getPosition().x - z.position.x;
            float dy = z.target.getPosition().y - z.position.y;

            if (dx * dx + dy * dy > BREAK_R2) {
                z.transitionState(CHASE);
                return;
            }

            if (z.attackTimer <= 0f) {
                z.target.applyDamage(z.attackDamage);
                z.attackTimer = z.attackCooldown;
            }
        }
        @Override public void enter(Zombie z) { z.attackTimer = 0.25f; }
        @Override public void exit(Zombie z) {}
    },

    DEAD {
        @Override
        public void enter(Zombie z) {
            z.active = false;
        }
        @Override public void update(Zombie z, float dt) {}
        @Override public void exit(Zombie z) {}
    }
}
