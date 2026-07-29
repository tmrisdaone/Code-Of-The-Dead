package com.survivalz.core;

/**
 * Mutable 2-D vector used for all world-space positions.
 * Reusing this avoids GC pressure from millions of dx/dy calculations.
 */
public final class Vector2 {
    public float x;
    public float y;

    public Vector2() {}

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /** Returns squared distance to (ox, oy) to skip sqrt when possible. */
    public float dist2(float ox, float oy) {
        float dx = this.x - ox;
        float dy = this.y - oy;
        return dx * dx + dy * dy;
    }
}
