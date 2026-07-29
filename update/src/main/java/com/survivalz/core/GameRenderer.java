package com.survivalz.core;

/** Contract for the host platform to render a frame given the current world. */
public interface GameRenderer {
    void render(GameWorld world);
}