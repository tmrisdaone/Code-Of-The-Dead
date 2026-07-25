package com.codzombies.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.codzombies.CodZombiesGame;
import com.codzombies.Constants;

/**
 * Desktop launcher for testing the game on PC.
 * Controls:
 *   WASD  - Move
 *   Mouse - Look (click to capture cursor)
 *   LMB   - Fire
 *   RMB   - ADS
 *   R     - Reload
 *   Q/E   - Switch weapon
 *   F     - Interact (buy door, wall buy)
 *   ESC   - Toggle cursor capture
 */
public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Code Of The Dead");
        config.setWindowedMode(Constants.VIEWPORT_WIDTH, Constants.VIEWPORT_HEIGHT);
        config.setForegroundFPS(60);
        config.useVsync(true);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 2);

        new Lwjgl3Application(new CodZombiesGame(), config);
    }
}
