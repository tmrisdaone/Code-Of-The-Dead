package com.codzombies.android;

import android.os.Bundle;
import android.view.View;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.codzombies.CodZombiesGame;
import com.codzombies.GameHUD;

/**
 * Android Activity that hosts the LibGDX game.
 * Forwards touch events from the GLSurfaceView to the GameHUD input system.
 */
public class AndroidLauncher extends AndroidApplication {

    private CodZombiesGame game;
    private GameHUD hud;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGyroscope = false;
        config.numSamples = 2; // MSAA for smoother edges

        // Create the game
        game = new CodZombiesGame();
        initialize(game, config);

        // Set up immersive mode (hide nav bar, status bar)
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(
                visibility -> setImmersiveMode(decorView));
        setImmersiveMode(decorView);
    }

    private void setImmersiveMode(View decorView) {
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        View decorView = getWindow().getDecorView();
        setImmersiveMode(decorView);
    }
}
