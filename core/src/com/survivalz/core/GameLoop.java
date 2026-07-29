package com.survivalz.core;


/**
 * Fixed-timestep game loop.
 * Runs the GameWorld update at a fixed rate independent of frame rate.
 */
public class GameLoop implements Runnable {

    private final GameWorld world;
    private volatile boolean running = false;

    public GameLoop(GameWorld world) {
        this.world = world;
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - lastTime) / 1_000_000_000f;
            lastTime = now;

            // Clamp dt to avoid spiral-of-death if the app resumes from pause
            if (dt > 0.25f) dt = 0.25f;

            world.update(dt);
        }
    }

    public void start() {
        running = true;
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
