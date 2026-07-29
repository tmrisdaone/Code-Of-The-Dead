package com.survivalz.core;

/**
 * Fixed-timestep game loop with accumulator.
 * Host creates one instance and calls tick(deltaTime) from its render thread.
 */
public class GameLoop {
    private static final float FIXED_DT = 1f / 60f; // 60 Hz sim
    private static final float MAX_DT = 0.25f;      // clamp spiral-of-death

    private final GameWorld world;
    private final GameInputPoll input;
    private final GameRenderer renderer;
    private float accumulator = 0f;

    public GameLoop(GameWorld world, GameInputPoll input, GameRenderer renderer) {
        this.world = world;
        this.input = input;
        this.renderer = renderer;
    }

    public void tick(float deltaTime) {
        deltaTime = Math.min(deltaTime, MAX_DT);
        accumulator += deltaTime;

        while (accumulator >= FIXED_DT) {
            GameWorld.InputState in = input.poll();
            world.update(FIXED_DT, in);
            accumulator -= FIXED_DT;
        }

        renderer.render(world);
    }
}