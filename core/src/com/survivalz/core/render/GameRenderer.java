package com.survivalz.core.render;

import com.survivalz.core.GameWorld;

/**
 * Interface for rendering the game world.
 * Decouples the game model from any specific rendering backend
 * (LibGDX 3D, Canvas 2D, OpenGL, etc.).
 */
public interface GameRenderer {
    /** Called once per frame after the game world updates. */
    void render(GameWorld world, float deltaTime);

    /** Called when the viewport is resized. */
    void resize(int width, int height);

    /** Cleanup resources. */
    void dispose();
}
