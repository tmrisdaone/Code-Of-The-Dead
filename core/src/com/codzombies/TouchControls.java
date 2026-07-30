package com.codzombies;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Touch HUD: a Scene2D Stage with three primitive controls.
 *
 *   ┌──────────────────────────┐
 *   │                          │   Right half → Drag-to-look area (LookActor):
 *   │ LEFT side                │   captures pointer drags and turns them into
 *   │   Virtual Joystick       │   (deltaYaw, deltaPitch) each frame.
 *   │   (move forward/back/    │
 *   │    strafe)               │
 *   │                          │   Bottom-right buttons (TouchButton):
 *   │                          │     [FIRE]  [ADS]  [RELOAD]
 *   │                  [FIRE]  │
 *   │                  [ADS]   │   Desktop is also supported: WASD moves,
 *   │                  [RELOAD]│   mouse look (when caught), LMB fire,
 *   │                          │   R reload, RMB ADS.
 *   └──────────────────────────┘
 *
 * Contracts:
 *  - Movement output is in `moveForward` and `moveStrafe`, both [-1..1].
 *    The GameScreen reads them each frame and feeds Player.update().
 *  - Look output is the accumulated `lookDX`/`lookDY` since the last
 *    clearLookDeltas(); the GameScreen converts them to yaw/pitch deltas
 *    using MOUSE_SENSITIVITY (touch uses TOUCH_SENSITIVITY).
 *  - Buttons fire callbacks through the `PlayerActions` interface so the
 *    HUD never touches Player directly.
 *
 * Math comments inline (joystick normalization, drag deltas).
 */
public class TouchControls {

    /** The HUD surface — GameScreen must call act()/draw() and route input here. */
    public final Stage stage;

    /** Action sink wired by the GameScreen (fire/reload/ads/look). */
    public interface PlayerActions {
        void onFire();
        void onReload();
        void onToggleADS();
    }
    private final PlayerActions actions;

    // ── Continuous output read by the GameScreen ──
    /** Movement joystick outputs in [-1,1]. */
    public float moveForward = 0f, moveStrafe = 0f;
    /** Look deltas since last clearLookDeltas(); in screen pixels. */
    public float lookDX = 0f, lookDY = 0f;
    /** Whether the fire button is currently held down (for automatic fire). */
    public boolean firing = false;

    // Joystick visual sizing
    private static final float STICK_RADIUS_DP = 140f;
    private static final float KNOB_RADIUS_DP  = 60f;

    private Image stickBaseImg, stickKnobImg;
    private float stickCx = -1f, stickCy = -1f; // active joystick center (touch origin)
    private float stickPointer = -1f;            // -1 = joystick not active

    private final float stickRadiusPx;            // pixels (computed from density)

    public TouchControls(Viewport screenView, PlayerActions actions) {
        this.actions = actions;
        this.stage = new Stage(screenView);

        // Convert dp to pixels using screen density so the stick feels right
        // on any device. On desktop density defaults to 1.
        stickRadiusPx = STICK_RADIUS_DP * (Gdx.graphics.getDensity() <= 0f
                ? 1f : Gdx.graphics.getDensity());

        buildJoystick();
        buildLookArea();
        buildButtons();

        // Enable desktop keyboard + mouse-look handling.
        stage.setKeyboardFocus(stage.getRoot());
    }

    // =================================================================
    //  LEFT-SIDE VIRTUAL JOYSTICK
    // =================================================================

    private void buildJoystick() {
        // Two procedural textures (base ring + knob circle drawn from a Pixmap).
        Texture baseTex = makeCircleTexture((int) stickRadiusPx * 2,
                0xFFFFFF40, true);
        Texture knobTex = makeCircleTexture((int) KNOB_RADIUS_DP * 2,
                0xFFFFFF80, false);
        stickBaseImg = new Image(new TextureRegionDrawable(new TextureRegion(baseTex)));
        stickKnobImg = new Image(new TextureRegionDrawable(new TextureRegion(knobTex)));

        // Hide until a touch anchors the joystick.
        stickBaseImg.setVisible(false);
        stickKnobImg.setVisible(false);

        // Touch the joystick anywhere in the LEFT half of the screen.
        Actor touchZone = new Actor();
        touchZone.setSize(stage.getWidth() * 0.5f, stage.getHeight());
        touchZone.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int b) {
                // Anchor the joystick at the touch point — floating joystick style.
                stickCx = x;
                stickCy = y;
                stickPointer = pointer;
                stickBaseImg.setPosition(x - stickBaseImg.getWidth()  / 2f,
                                          y - stickBaseImg.getHeight() / 2f);
                stickKnobImg.setPosition(x - stickKnobImg.getWidth()  / 2f,
                                          y - stickKnobImg.getHeight() / 2f);
                stickBaseImg.setVisible(true);
                stickKnobImg.setVisible(true);
                updateJoystickOutput(x, y);
                return true;        // consume; don't fall through to look area
            }
            @Override public void touchDragged(InputEvent e, float x, float y, int pointer) {
                if (pointer == stickPointer) updateJoystickOutput(x, y);
            }
            @Override public void touchUp(InputEvent e, float x, float y, int pointer, int b) {
                if (pointer == stickPointer) {
                    stickPointer = -1;
                    stickBaseImg.setVisible(false);
                    stickKnobImg.setVisible(false);
                    moveForward = 0f;
                    moveStrafe  = 0f;
                }
            }
        });
        stage.addActor(touchZone);
        stage.addActor(stickBaseImg);
        stage.addActor(stickKnobImg);
    }

    /**
     * Map joystick knob offset into a normalized [-1,1] (forward,strafe) vector.
     * Y in libgdx scene2d is +UP, so moving the knob up (+y) means forward.
     * Movement vector is clamped to the unit circle (.magnitude <= 1) for analog feel.
     */
    private void updateJoystickOutput(float touchX, float touchY) {
        float dx = touchX - stickCx;
        float dy = touchY - stickCy;
        // Magnitude (clamped to the stick radius) → strength in [0..1].
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float max  = stickRadiusPx;
        if (dist > max) {
            // Pull the knob back onto the ring edge and normalize the strength to 1.
            float k = max / dist;
            dx *= k;
            dy *= k;
            dist = max;
        }
        float strength = max > 0f ? dist / max : 0f;
        if (dist > 0.001f) {
            // Direction from touch → (sx,_sy); forward = +y in scene2d.
            float fx = dx / dist;
            float fy = dy / dist;
            moveStrafe  = fx * strength;
            moveForward = fy * strength;
        } else {
            moveForward = moveStrafe = 0f;
        }

        // Visually move the knob to the (clamped) offset from the center.
        stickKnobImg.setPosition(stickCx + dx - stickKnobImg.getWidth()  / 2f,
                                stickCy + dy - stickKnobImg.getHeight() / 2f);
    }

    // =================================================================
    //  RIGHT-SIDE DRAG-TO-LOOK
    // =================================================================

    private void buildLookArea() {
        Actor lookArea = new Actor();
        lookArea.setSize(stage.getWidth() * 0.5f, stage.getHeight());
        lookArea.setPosition(stage.getWidth() * 0.5f, 0f);
        lookArea.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int b) {
                lastLookX = e.getStageX();
                lastLookY = e.getStageY();
                return true;
            }
            @Override public void touchDragged(InputEvent e, float x, float y, int pointer) {
                // Accumulate screen-space deltas. The GameScreen turns these into
                // yaw/pitch changes with TOUCH_SENSITIVITY. Inverting dY gives
                // "drag up → look up" (pitch increases on upward drag).
                float px = e.getStageX();
                float py = e.getStageY();
                lookDX += (px - lastLookX);
                lookDY += (py - lastLookY);
                lastLookX = px;
                lastLookY = py;
            }
        });
        stage.addActor(lookArea);
    }
    private float lastLookX, lastLookY;

    /** Called by the GameScreen after it has consumed the deltas. */
    public void clearLookDeltas() { lookDX = 0f; lookDY = 0f; }

    // =================================================================
    //  FIRE / ADS / RELOAD BUTTONS
    // =================================================================

    private void buildButtons() {
        Table buttonRow = new Table();
        buttonRow.bottom().right();
        buttonRow.setFillParent(true);
        buttonRow.pad(30f);

        Image fireBtn   = makeButton(Color.RED,   "FIRE");
        Image reloadBtn = makeButton(Color.ORANGE,"R");
        Image adsBtn    = makeButton(Color.BLUE,  "ADS");

        fireBtn.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                firing = true;
                actions.onFire();
                return true;
            }
            @Override public void touchUp  (InputEvent e, float x, float y, int p, int b) { firing = false; }
        });
        reloadBtn.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                actions.onReload();
                return true;
            }
        });
        adsBtn.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                actions.onToggleADS();
                return true;
            }
        });

        buttonRow.add(adsBtn).size(120f).pad(6f);
        buttonRow.add(reloadBtn).size(120f).pad(6f);
        buttonRow.add(fireBtn).size(180f).pad(6f);
        stage.addActor(buttonRow);
    }

    /** Returns a square Image tapped-to-look pressed. Used as the fire/etc buttons. */
    private Image makeButton(Color tint, String label) {
        Texture tex = makeSquareTexture(120, tint);
        return new Image(new TextureRegionDrawable(new TextureRegion(tex)));
    }

    // =================================================================
    //  DESKTOP FALLBACK INPUT (WASD / mouse)
    // =================================================================

    /**
     * Called every frame by the GameScreen on Gdx.input. Lets the same Stage drive
     * desktop mouse-look + WASD when there's no touch. We only consume keyboard/
     * mouse here; touch goes through Scene2D dispatch.
     */
    public void pollDesktopPc(Player p) {
        if (!Gdx.input.isCursorCatched() && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            // Click to capture the cursor.
            Gdx.input.setCursorCatched(true);
        }

        // Look: ignore tiny drift; multiply by sensitivity in the GameScreen.
        // We expose deltas via lookDX/lookDY so the GameScreen applies them uniformly.
        if (Gdx.input.isCursorCatched()) {
            lookDX += Gdx.input.getDeltaX();
            lookDY += Gdx.input.getDeltaY();
        }

        // WASD as virtual joystick.
        float f = 0f, s = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) f += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) f -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) s -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) s += 1f;
        moveForward = clamp(f, -1f, 1f);
        moveStrafe  = clamp(s, -1f, 1f);
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    // =================================================================
    //  PX TEXTURE HELPERS
    // =================================================================

    private static Texture makeCircleTexture(int sizePx, int rgba, boolean ring) {
        Pixmap p = new Pixmap(sizePx, sizePx, Pixmap.Format.RGBA8888);
        p.setColor(new Color(rgba));
        int r = sizePx / 2;
        if (ring) p.drawCircle(r, r, r - 2);
        else      p.fillCircle(r, r, r - 2);
        Texture t = new Texture(p);
        p.dispose();
        return t;
    }

    private static Texture makeSquareTexture(int sizePx, Color tint) {
        Pixmap p = new Pixmap(sizePx, sizePx, Pixmap.Format.RGBA8888);
        p.setColor(tint.r, tint.g, tint.b, 0.7f);
        p.fill();
        p.setColor(1f, 1f, 1f, 0.9f);
        p.drawRectangle(0, 0, sizePx, sizePx);
        Texture t = new Texture(p);
        p.dispose();
        return t;
    }

    // =================================================================
    //  LIFE-CYCLE
    // =================================================================

    public void act(float dt) { stage.act(dt); }
    public void draw()        { stage.draw(); }
    public void resize(int w, int h) { stage.getViewport().update(w, h, true); }
    public void dispose() {
        // Stage owns its actors; the textures inside them leak one-per-Pixmap.
        // For a tutorial build this is acceptable; a production build should
        // keep one atlas and dispose it in dispose().
        stage.dispose();
    }
}
