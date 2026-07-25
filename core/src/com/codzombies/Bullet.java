package com.codzombies;

import com.badlogic.gdx.math.Vector3;

/**
 * 3D bullet/projectile entity.
 * Used by Ray Gun for splash-damage projectiles.
 */
public class Bullet {

    public final Vector3 position  = new Vector3();
    public final Vector3 direction = new Vector3();
    public float   speed;
    public float   damage;
    public float   splashRadius;
    public float   lifetime;       // seconds remaining
    public float   maxLifetime;
    public int     ownerIndex;     // 0 = player

    public void set(
            float px, float py, float pz,
            float dx, float dy, float dz,
            float speed, float damage,
            float splashRadius, float lifetime
    ) {
        position.set(px, py, pz);
        direction.set(dx, dy, dz).nor();
        this.speed        = speed;
        this.damage       = damage;
        this.splashRadius = splashRadius;
        this.lifetime     = lifetime;
        this.maxLifetime  = lifetime;
        this.ownerIndex   = 0;
    }

    public void reset() {
        position.set(0, 0, 0);
        direction.set(0, 0, 0);
        speed  = 0;
        damage = 0;
        splashRadius = 0;
        lifetime = 0;
        maxLifetime = 0;
        ownerIndex = -1;
    }
}
