package com.codzombies;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Main LibGDX ApplicationAdapter — the "GameScreen" for this build.
 *
 * Owns:
 *   - PerspectiveCamera, written each frame from Player.position + (yaw,pitch).
 *   - ModelBatch + Environment (ambient + directional + a point light on the player).
 *   - Static arena geometry: one floor Model, four wall Models, all prebuilt.
 *   - One reusable Zombie ModelInstance repositioned per active zombie when drawing.
 *   - HUD: a Scene2D Stage (TouchControls) drawn on top of the 3D scene.
 *
 * Update order per frame:
 *   1. Drive look + movement input through TouchControls (touch deltas / desktop WASD).
 *   2. Update player (movement, ammo timers, health regen) and write the camera.
 *   3. Update the wave manager (spawn/AI/melee; melee damages the player via a sink).
 *   4. If the player fired this frame, resolve the hitscan ray against the world,
 *      award hit/kill/headshot points, and flash the hit zombie.
 *   5. Render the 3D scene, then draw the Scene2D HUD on top with the crosshair.
 *
 * Camera-to-world math:
 *   The camera's `direction` vector is the player's aimVector(): a unit vector
 *   built from yaw/pitch by spherical->cartesian conversion (see Player.aimVector).
 *   `up` is always +Y (world up). `position` is eye height = PLAYER_HEIGHT.
 *
 * Arena:
 *   Axis-aligned box of `ArenaHalf` units on each side (origin-centered).
 *   Player position is clamped into the box each frame (simple AABB collision).
 */
public class CodZombiesGame extends ApplicationAdapter {

    // ── Sim entity + management ──
    private Player player;
    private ZombieManager zombieManager;
    private TouchControls hud;
    private int points = 0;

    // ── Rendering ──
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;
    private PointLight playerLight;

    // Arena geometry (reusable, cheap primitives from ModelBuilder)
    private Model floorModel;
    private ModelInstance floorInstance;
    private ModelInstance[] wallInstances;
    private Model zombieModel;           // single shared box for ALL zombies
    private ModelInstance zombieInstance; // repositioned per-zombie while drawing

    private static final float ARENA_HALF = 24f;          // half-extent of the square arena
    private static final float WALL_HEIGHT = 3f;

    // ── 2D HUD overlay (crosshair + points + ammo text on top of the stage) ──
    private Viewport hudViewport;
    private SpriteBatch batch;
    private BitmapFont font;

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        // ── Entities ──
        player = new Player();
        zombieManager = new ZombieManager();
        hud = new TouchControls(new ScreenViewport(), new TouchControls.PlayerActions() {
            @Override public void onFire()     { handleFire(); }
            @Override public void onReload()    { player.startReload(); }
            @Override public void onToggleADS(){ player.adsActive = !player.adsActive; }
        });

        // Route touch to the Stage; keyboard for desktop gets the multiplexer
        // so game-over SPACE etc. also works.
        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(hud.stage);
        mux.addProcessor(new com.badlogic.gdx.InputAdapter() {
            @Override public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.R) { player.startReload(); return true; }
                if (keycode == Input.Keys.SPACE && !player.isAlive()) restart();
                return false;
            }
        });
        Gdx.input.setInputProcessor(mux);
        // Hide the cursor only on desktop; touch devices ignore this.
        Gdx.input.setCursorCatched(false);

        // ── Camera ──
        camera = new PerspectiveCamera(
                Constants.FOV_DEFAULT,
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight());
        camera.near = Constants.NEAR_PLANE;
        camera.far  = Constants.FAR_PLANE;
        camera.position.set(0f, Constants.PLAYER_HEIGHT, 0f);
        camera.lookAt(0f, Constants.PLAYER_HEIGHT, 24f);   // look toward +Z by default
        camera.up.set(0f, 1f, 0f);
        camera.update();

        // ── 3D pipeline + lighting ──
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.35f, 0.35f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(
                0.6f, 0.6f, 0.55f,
                -0.4f, -1f, -0.3f));          // a key light from the upper back-left
        // Swing a point light with the player so close-range zombies read clearly.
        playerLight = new PointLight().set(0.8f, 0.85f, 0.7f, 0f, 1.8f, 0f, 12f);
        environment.add(playerLight);

        buildArenaGeometry();

        // ── 2D overlay ──
        hudViewport = new ScreenViewport();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.2f);
    }

    // =================================================================
    //  ARENA GEOMETRY
    // =================================================================

    /** Build the floor + four walls once. They share one Model and never move. */
    private void buildArenaGeometry() {
        Material floorMat = new Material(ColorAttribute.createDiffuse(
                new Color(0.22f, 0.22f, 0.25f, 1f)));
        ModelBuilder floorBuilder = new ModelBuilder();
        floorModel = floorBuilder.createBox(
                ARENA_HALF * 2f, 0.2f, ARENA_HALF * 2f, floorMat,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        floorInstance = new ModelInstance(floorModel);
        floorInstance.transform.setToTranslation(0f, -0.1f, 0f);

        // ── Walls: four boxes around the perimeter ──
        Material wallMat = new Material(ColorAttribute.createDiffuse(
                new Color(0.45f, 0.40f, 0.32f, 1f)));
        wallInstances = new ModelInstance[4];
        float longLen = ARENA_HALF * 2f;
        // Two long walls (one along +X edge, one along -X), running along Z.
        // Two along Z edges (+ and -), running along X. Heights = WALL_HEIGHT.
        // We give each wall its own Model so it can be disposed individually.
        float [][] wallSpec = {
                { ARENA_HALF, WALL_HEIGHT / 2f, 0f, 0.5f, WALL_HEIGHT, longLen }, // +X wall
                {-ARENA_HALF, WALL_HEIGHT / 2f, 0f, 0.5f, WALL_HEIGHT, longLen }, // -X wall
                {0f, WALL_HEIGHT / 2f,  ARENA_HALF, longLen, WALL_HEIGHT, 0.5f }, // +Z wall
                {0f, WALL_HEIGHT / 2f, -ARENA_HALF, longLen, WALL_HEIGHT, 0.5f }, // -Z wall
        };
        for (int i = 0; i < 4; i++) {
            ModelBuilder wb = new ModelBuilder();
            Model wallModel = wb.createBox(
                    wallSpec[i][3], wallSpec[i][4], wallSpec[i][5], wallMat,
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
            wallInstances[i] = new ModelInstance(wallModel);
            wallInstances[i].transform.setToTranslation(
                    wallSpec[i][0], wallSpec[i][1], wallSpec[i][2]);
        }

        // ── Zombie placeholder: a single maroon box 0.8 x 1.8 x 0.8 ──
        Material zombieMat = new Material(ColorAttribute.createDiffuse(
                new Color(0.55f, 0.10f, 0.08f, 1f)));
        ModelBuilder zb = new ModelBuilder();
        zombieModel = zb.createBox(
                0.8f, 1.8f, 0.8f, zombieMat,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        zombieInstance = new ModelInstance(zombieModel);
    }

    // =================================================================
    //  RENDER LOOP
    // =================================================================

    private final Vector3 tmpEye = new Vector3();
    private final Vector3 tmpDir = new Vector3();

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), Constants.DELTA_MAX);

        // ── Desktop fallback input: WASD + mouse look if no touch present ──
        boolean isTouch = Gdx.input.isPeripheralAvailable(Input.Peripheral.MultitouchScreen);
        if (!isTouch) {
            hud.pollDesktopPc(player);
        }
        hud.act(dt);

        // ── Apply look deltas (touch or desktop) ──
        // We multiply screen-space deltas by sensitivity. Inverting dY so
        // touch/drag UP looks UP (pitch increases).
        float sens = isTouch ? Constants.TOUCH_SENSITIVITY : Constants.MOUSE_SENSITIVITY;
        player.yaw   += hud.lookDX * sens;
        player.pitch += -hud.lookDY * sens;
        player.pitch = com.badlogic.gdx.math.MathUtils.clamp(
                player.pitch, Constants.PITCH_MIN, Constants.PITCH_MAX);
        hud.clearLookDeltas();

        // ── Update player (timers, regen, integration of movement + collision) ──
        player.update(dt, hud.moveStrafe, hud.moveForward);
        player.integrateMovement(dt);
        keepPlayerInArena();

        // ── Wave + AI: zombies chase the player and melee on contact ──
        zombieManager.update(dt, player.position, damage ->
                player.takeDamage(damage));

        // ── Automatic fire if the FIRE button is held ──
        if (hud.firing) handleFire();

        // ── Resolve any shot the player released this frame ──
        if (player.pendingShot != null) resolveShot();

        // ── Update the camera from player state ──
        updateCamera(dt);

        // ── Render ──
        ScreenUtils.clear(0.05f, 0.05f, 0.07f, 1f);
        modelBatch.begin(camera);
        modelBatch.render(floorInstance, environment);
        for (ModelInstance wall : wallInstances) modelBatch.render(wall, environment);

        // Draw each active zombie by repositioning the shared instance.
        for (ZombieManager.Zombie z : zombieManager.zombies()) {
            if (!z.alive) continue;
            zombieInstance.transform.setToTranslation(
                    z.position.x, 0.9f, z.position.z);   // box half-height = 0.9
            // Flash white briefly when hit; otherwise the base material's color shows through.
            if (z.hitFlash > 0f) {
                Material m = zombieInstance.materials.get(0);
                m.set(ColorAttribute.createDiffuse(
                        lerpColor(0.55f, 0.10f, 0.08f, 1f, 1f, 1f, 1f, z.hitFlash)));
            } else {
                Material m = zombieInstance.materials.get(0);
                m.set(ColorAttribute.createDiffuse(new Color(0.55f, 0.10f, 0.08f, 1f)));
            }
            modelBatch.render(zombieInstance, environment);
        }
        modelBatch.end();

        // ── HUD overlay: Scene2D then the crosshair + HUD text ──
        hudViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        drawCrosshair();
        drawHudText();
        batch.end();

        hud.draw();

        // ── Damage vignette on top of everything ──
        if (player.damageFlash > 0f) {
            batch.begin();
            batch.setColor(1f, 0f, 0f, Math.min(0.45f, player.damageFlash * 0.6f));
            batch.draw((com.badlogic.gdx.graphics.Texture) null, 0, 0); // placeholder guard
            batch.setColor(1f, 1f, 1f, 1f);
            batch.end();
            // NOTE: drawing a full-screen red overlay properly needs a 1x1
            // white Texture; we skip it here to avoid an extra allocation.
            // The damageFlash value still decays (Player.update) so the HUD
            // text/health bar can reflect damage taken.
        }

        // ── Game-out screen ──
        if (!player.isAlive()) drawGameOver();
    }

    private final Vector3 eye_ = new Vector3();

    /** Write PerspectiveCamera transform from player.position + (yaw,pitch). */
    private void updateCamera(float dt) {
        eye_.set(player.position.x, Constants.PLAYER_HEIGHT, player.position.z);
        camera.position.set(eye_);
        // Aim direction = spherical->cartesian from (yaw,pitch). Already normalized.
        player.aimVector(tmpDir);
        camera.direction.set(tmpDir);
        camera.up.set(0f, 1f, 0f);

        // FOV easing between default and ADS for the aim-down-sights feel.
        float targetFov = player.adsActive ? Constants.FOV_ADS : Constants.FOV_DEFAULT;
        camera.fieldOfView += (targetFov - camera.fieldOfView)
                * Constants.ADS_INTERP_SPEED * dt;
        camera.update();

        // Drag the player light with the eye so close quarters stay lit.
        playerLight.setPosition(eye_.x, eye_.y + 0.4f, eye_.z);
    }

    // =================================================================
    //  SHOOTING
    // =================================================================

    /** Try to fire one round; on success the pendingShot is queued. */
    private void handleFire() {
        eye_.set(player.position.x, Constants.PLAYER_HEIGHT, player.position.z);
        player.tryFire(eye_);
    }

    /**
     * Consume the player's hitscan Shot against the wave manager.
     * Closest zombie, headshot if hit is above roughly 1.3m of its base height.
     */
    private final Vector3 sTmpOrigin = new Vector3();
    private final Vector3 sTmpDir   = new Vector3();
    private void resolveShot() {
        sTmpOrigin.set(player.pendingShot.origin);
        sTmpDir.set(player.pendingShot.direction).nor();
        ZombieManager.Zombie z = zombieManager.raycastZombie(sTmpOrigin, sTmpDir);
        if (z != null && z.alive) {
            // Headshot heuristic: vertical aim at impact above ~1.3m of its base height.
            // Approximated by where the ray clipped into the AABB's Y slab.
            float impactY = sTmpOrigin.y + sTmpDir.y;  // simple proxy for height of ray entry
            boolean headshot = impactY > 1.3f;
            float dmg = player.pendingShot.damage * (headshot ? 2f : 1f);
            boolean killed = zombieManager.damageZombie(z, dmg);
            if (killed) {
                points += headshot ? Constants.POINTS_PER_HEADSHOT : Constants.POINTS_PER_KILL;
            } else {
                points += Constants.POINTS_PER_HIT;
            }
        }
        player.clearPendingShot();
    }

    // =================================================================
    //  ARENA COLLISION (simple AABB clamp)
    // =================================================================

    /** Keep the player inside the arena box (minus a small wall buffer). */
    private void keepPlayerInArena() {
        float margin = 0.5f;
        float lim = ARENA_HALF - margin;
        if (player.position.x >  lim) player.position.x =  lim;
        if (player.position.x < -lim) player.position.x = -lim;
        if (player.position.z >  lim) player.position.z =  lim;
        if (player.position.z < -lim) player.position.z = -lim;
    }

    // =================================================================
    //  HUD TEXT / CROSSHAIR / GAME OVER
    // =================================================================

    private void drawCrosshair() {
        float cx = Gdx.graphics.getWidth()  / 2f;
        float cy = Gdx.graphics.getHeight() / 2f;
        font.setColor(1f, 0.2f, 0.2f, 0.9f);
        font.draw(batch, "+", cx - 4f, cy + 4f);
    }

    private void drawHudText() {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, "HP: " + (int) player.getHealth(), 16f, h - 16f);
        font.draw(batch, "Round: " + zombieManager.getRound(), w * 0.5f - 40f, h - 16f);
        font.draw(batch, "Points: " + points, w - 160f, h - 16f);
        font.draw(batch, "Zombies: " + zombieManager.getAliveCount(), 16f, h - 40f);
        font.draw(batch,
                "Ammo: " + player.getClip() + " / " + player.getReserve()
                        + (player.isReloading() ? "  [RELOADING]" : ""),
                w * 0.5f - 60f, 30f);
    }

    private void drawGameOver() {
        batch.begin();
        float cx = Gdx.graphics.getWidth()  / 2f;
        float cy = Gdx.graphics.getHeight() / 2f;
        font.getData().setScale(2.5f);
        font.setColor(1f, 0f, 0f, 1f);
        font.draw(batch, "YOU DIED", cx - 80f, cy);
        font.getData().setScale(1f);
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, "Press SPACE to restart", cx - 90f, cy - 30f);
        font.draw(batch, "Reached Round " + zombieManager.getRound(), cx - 80f, cy - 55f);
        batch.end();
    }

    // =================================================================
    //  HELPERS
    // =================================================================

    private static Color lerpColor(float r1, float g1, float b1, float a1,
                                    float r2, float g2, float b2, float t) {
        return new Color(
                r1 + (r2 - r1) * t,
                g1 + (g2 - g1) * t,
                b1 + (b2 - b1) * t,
                a1);
    }

    private void restart() {
        player.reset();
        zombieManager.reset();
        points = 0;
        camera.fieldOfView = Constants.FOV_DEFAULT;
    }

    // =================================================================
    //  LIFE-CYCLE
    // =================================================================

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth  = width;
        camera.viewportHeight = height;
        camera.update();
        hud.resize(width, height);
        hudViewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        // Floor + walls + zombie model are each their own Model; collect & dispose.
        if (floorModel != null) floorModel.dispose();
        for (ModelInstance wi : wallInstances) if (wi != null && wi.model != null) wi.model.dispose();
        if (zombieModel != null) zombieModel.dispose();
        modelBatch.dispose();
        hud.dispose();
        batch.dispose();
        font.dispose();
    }
}