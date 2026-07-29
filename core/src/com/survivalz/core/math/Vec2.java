package com.survivalz.core.math;

/**
 * Mutable 2-D vector used for all world-space positions.
 * Reusing this avoids GC pressure from millions of dx, dy calculations.
 */
public final class Vec2 {
    public float x;
    public float y;

    public Vec2() {}

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void set(Vec2 other) {
        this.x = other.x;
        this.y = other.y;
    }

    public void add(float x, float y) {
        this.x += x;
        this.y += y;
    }

    public void add(Vec2 other) {
        this.x += other.x;
        this.y += other.y;
    }

    public void sub(float x, float y) {
        this.x -= x;
        this.y -= y;
    }

    public void sub(Vec2 other) {
        this.x -= other.x;
        this.y -= other.y;
    }

    public void scl(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
    }

    /**
     * Returns squared distance to (ox, oy) to skip sqrt when possible.
     */
    public float dist2(float ox, float oy) {
        float dx = this.x - ox;
        float dy = this.y - oy;
        return dx * dx + dy * dy;
    }

    public float dist2(Vec2 other) {
        return dist2(other.x, other.y);
    }

    public float dist(float ox, float oy) {
        return (float) Math.sqrt(dist2(ox, oy));
    }

    public float len() {
        return (float) Math.sqrt(x * x + y * y);
    }

    public float len2() {
        return x * x + y * y;
    }

    public void nor() {
        float len = len();
        if (len > 0f) {
            x /= len;
            y /= len;
        }
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
