package com.survivalz.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import com.survivalz.core.config.BalanceConfig;
import com.survivalz.core.entity.Player;
import com.survivalz.core.entity.Zombie;
import com.survivalz.core.round.RoundManager;
import com.survivalz.core.weapon.Weapon;
import com.survivalz.core.render.GameHUD;
import com.survivalz.core.weapon.WeaponSystem;

/**
 * Main game class — bridges the new com.survivalz.core architecture
 * with LibGDX 3D rendering. Uses GameWorld for all game logic.
 */
public class SurvivalzGame extends ApplicationAdapter implements InputProcessor {

    // ── Core game ────────────────────────────────────────────
    private GameWorld world;
    private GameHUD gameHud;

    // ── 3D rendering ────────────────────────────────────────
    private ModelBatch      modelBatch;
    private Environment     environment;
    private ShapeRenderer   shapes;
    private PerspectiveCamera camera;
    private ModelInstance[][] wallModels; // [tileZ][tileX] — null = removed/door opened
    private ModelInstance   floorInstance;
    private Model           floorModel;
    private Model           zombieModel;
    private ModelInstance   zombieVisual;

    // ── 2D HUD ───────────────────────────────────────────────
    private OrthographicCamera hudCam;
    private SpriteBatch  batch;
    private BitmapFont   font;
    private GlyphLayout  layout;
    private float screenWidth;
    private float screenHeight;

    // ── FPS Camera State ─────────────────────────────────────
    private float yaw   = 0f;
    private float pitch = 0f;
    private float fovCurrent = BalanceConfig.FOV_DEFAULT;
    private boolean adsPressed = false;

    // ── Pre-built colors ─────────────────────────────────────
    private static final Color COLOR_WALL    = new Color(0.40f, 0.32f, 0.22f, 1f);
    private static final Color COLOR_BARRIER = new Color(0.50f, 0.30f, 0.10f, 1f);
    private static final Color COLOR_DOOR    = new Color(0.20f, 0.50f, 0.20f, 1f);
    private static final Color COLOR_WALLBUY = new Color(0.80f, 0.80f, 0.00f, 1f);
    private static final Color COLOR_ZOMBIE  = new Color(0.60f, 0.00f, 0.00f, 1f);
    private static final Color COLOR_HIT     = new Color(1.00f, 1.00f, 1.00f, 1f);

    private static final int TILE_EMPTY   = 0;
    private static final int TILE_WALL    = 1;
    private static final int TILE_DOOR    = 2;
    private static final int TILE_BARRIER = 3;
    private static final int TILE_WALLBUY = 4;

    // ── Player movement ──────────────────────────────────────
    private float moveX = 0f, moveY = 0f;
    private boolean firePressed = false;
    private boolean fireWasPressed = false;
    private boolean gameOver = false;

    @Override
    public void create() {
        // ── Core game world ──────────────────────────────
        world = new GameWorld();
        gameHud = new GameHUD(world);

        // ── Camera ────────────────────────────────────────────
        camera = new PerspectiveCamera(
                BalanceConfig.FOV_DEFAULT,
                BalanceConfig.VIEWPORT_WIDTH,
                BalanceConfig.VIEWPORT_HEIGHT
        );
        camera.near = BalanceConfig.NEAR_PLANE;
        camera.far = BalanceConfig.FAR_PLANE;

        // ── 3D renderer ─────────────────────────────────────
        modelBatch  = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(
                ColorAttribute.AmbientLight, 0.15f, 0.15f, 0.15f, 1f));
        environment.add(new DirectionalLight().set(
                0.6f, 0.6f, 0.6f, -0.5f, -1f, -0.5f));

        shapes = new ShapeRenderer();
        shapes.setAutoShapeType(true);

        // ── Floor model ─────────────────────────────────────
        ModelBuilder mb = new ModelBuilder();
        float mapWorld = BalanceConfig.MAP_SIZE * BalanceConfig.TILE_SIZE;
        floorModel = mb.createBox(
                mapWorld, 0.1f, mapWorld,
                new Material(ColorAttribute.createDiffuse(
                        new Color(0.25f, 0.25f, 0.25f, 1f))),
                VertexAttributes.Usage.Position
                        | VertexAttributes.Usage.Normal
        );
        floorInstance = new ModelInstance(floorModel);
        floorInstance.transform.setToTranslation(
                mapWorld / 2f, -0.1f, mapWorld / 2f);

        // ── Build wall models from GameWorld map data ────
        rebuildWalls();

        // ── Zombie model ────────────────────────────────────
        zombieModel = mb.createBox(
                0.6f, 1.6f, 0.6f,
                new Material(ColorAttribute.createDiffuse(COLOR_ZOMBIE)),
                VertexAttributes.Usage.Position
                        | VertexAttributes.Usage.Normal
        );
        zombieVisual = new ModelInstance(zombieModel);

        // ── 2D HUD ─────────────────────────────────────────
        hudCam = new OrthographicCamera();
        batch = new SpriteBatch();
        font = new BitmapFont();
        layout = new GlyphLayout();
        font.getData().setScale(1.2f);
        resize(BalanceConfig.VIEWPORT_WIDTH, BalanceConfig.VIEWPORT_HEIGHT);

        // ── Input ───────────────────────────────────────────
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCatchKey(Input.Keys.ESCAPE, true);

        // ── Subscribe to door-opened events for dynamic wall removal ──
        EventBus.INSTANCE.subscribe(GameEvent.Type.DOOR_OPENED, event -> {
            int[] coords = event.getData();
            int tx = coords[0];
            int ty = coords[1];
            removeWallVisual(tx, ty);
        });
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), BalanceConfig.DELTA_MAX);

        // ── Handle desktop input ─────────────────────────────
        handleDesktopInput();

        // ── Update game world ────────────────────────────────
        if (!gameOver) {
            world.setMoveInput(moveX, moveY);
            world.setFiring(firePressed);

            world.update(dt);
            gameHud.update(dt);

            if (world.isGameOver()) gameOver = true;

            if (world.getPlayer().getHealth() < BalanceConfig.PLAYER_HEALTH_MAX * 0.3f) {
                gameHud.triggerDamageFlash();
            }

            updateCamera(dt);
        }

        // ── Clear & render 3D scene ─────────────────────────
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        modelBatch.begin(camera);
        modelBatch.render(floorInstance, environment);

        // Render only visible (non-opened-door) wall tiles
        int[][] mapData = world.getMapData();
        for (int z = 0; z < BalanceConfig.MAP_SIZE; z++) {
            for (int x = 0; x < BalanceConfig.MAP_SIZE; x++) {
                ModelInstance inst = wallModels[z][x];
                if (inst != null && mapData[z][x] != TILE_EMPTY) {
                    modelBatch.render(inst, environment);
                }
            }
        }

        renderZombies();
        modelBatch.end();

        // ── Render 2D HUD ────────────────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        hudCam.update();
        shapes.setProjectionMatrix(hudCam.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawDamageVignette();
        shapes.end();

        batch.setProjectionMatrix(hudCam.combined);
        batch.begin();
        drawHUDText();
        drawRoundAnnouncement();
        batch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        if (gameOver) renderGameOver();
    }

    @Override
    public void resize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        hudCam.setToOrtho(false, width, height);
        hudCam.update();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        floorModel.dispose();
        zombieModel.dispose();
        disposeWalls();
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    // ═════════════════════════════════════════════════════════
    //  MAP / WALLS
    // ═════════════════════════════════════════════════════════

    private void rebuildWalls() {
        disposeWalls();
        wallModels = new ModelInstance[BalanceConfig.MAP_SIZE][BalanceConfig.MAP_SIZE];
        ModelBuilder mb = new ModelBuilder();

        int[][] mapData = world.getMapData();
        for (int y = 0; y < BalanceConfig.MAP_SIZE; y++) {
            for (int x = 0; x < BalanceConfig.MAP_SIZE; x++) {
                int tile = mapData[y][x];
                if (tile == TILE_EMPTY) continue;

                Color c;
                switch (tile) {
                    case TILE_WALL:    c = COLOR_WALL;    break;
                    case TILE_BARRIER: c = COLOR_BARRIER; break;
                    case TILE_DOOR:    c = COLOR_DOOR;    break;
                    case TILE_WALLBUY: c = COLOR_WALLBUY; break;
                    default:           c = COLOR_WALL;
                }
                Model box = mb.createBox(
                        BalanceConfig.TILE_SIZE, BalanceConfig.TILE_SIZE, BalanceConfig.TILE_SIZE,
                        new Material(ColorAttribute.createDiffuse(c)),
                        VertexAttributes.Usage.Position
                                | VertexAttributes.Usage.Normal
                );
                ModelInstance inst = new ModelInstance(box);
                inst.transform.setToTranslation(
                        x * BalanceConfig.TILE_SIZE + BalanceConfig.TILE_SIZE / 2f,
                        BalanceConfig.TILE_SIZE / 2f,
                        y * BalanceConfig.TILE_SIZE + BalanceConfig.TILE_SIZE / 2f
                );
                wallModels[y][x] = inst;
            }
        }
    }

    private void removeWallVisual(int tileX, int tileY) {
        if (tileY < 0 || tileY >= BalanceConfig.MAP_SIZE ||
            tileX < 0 || tileX >= BalanceConfig.MAP_SIZE) return;
        ModelInstance inst = wallModels[tileY][tileX];
        if (inst != null) {
            inst.model.dispose();
            wallModels[tileY][tileX] = null;
        }
    }

    private void disposeWalls() {
        if (wallModels != null) {
            for (int y = 0; y < BalanceConfig.MAP_SIZE; y++) {
                for (int x = 0; x < BalanceConfig.MAP_SIZE; x++) {
                    if (wallModels[y][x] != null) {
                        wallModels[y][x].model.dispose();
                    }
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════
    //  INPUT PROCESSOR
    // ═════════════════════════════════════════════════════════

    @Override public boolean touchDown(int sx, int sy, int ptr, int btn) { return false; }
    @Override public boolean touchDragged(int sx, int sy, int ptr) { return false; }
    @Override public boolean touchUp(int sx, int sy, int ptr, int btn) { return false; }
    @Override public boolean mouseMoved(int sx, int sy) { return false; }
    @Override public boolean scrolled(float ax, float ay) { return false; }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            if (!Gdx.input.isCursorCatched())
                Gdx.input.setCursorCatched(true);
            else
                Gdx.input.setCursorCatched(false);
            return true;
        }
        if (keycode == Input.Keys.R)          { world.setReloadPressed(); return true; }
        if (keycode == Input.Keys.Q || keycode == Input.Keys.E) { world.setWeaponSwitch(); return true; }
        if (keycode == Input.Keys.F)          { world.setInteractPressed(); return true; }
        if (keycode == Input.Keys.SPACE && gameOver) { resetGame(); return true; }
        return false;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char c)   { return false; }
    @Override public boolean touchCancelled(int sx, int sy, int ptr, int btn) { return false; }

    // ═════════════════════════════════════════════════════════
    //  DESKTOP INPUT
    // ═════════════════════════════════════════════════════════

    private void handleDesktopInput() {
        moveX = 0f;
        moveY = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) moveY = 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) moveY = -1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) moveX = -1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) moveX = 1f;

        if (Gdx.input.isCursorCatched()) {
            yaw   += Gdx.input.getDeltaX() * BalanceConfig.MOUSE_SENSITIVITY;
            pitch += -Gdx.input.getDeltaY() * BalanceConfig.MOUSE_SENSITIVITY;
            pitch = MathUtils.clamp(pitch, BalanceConfig.PITCH_MIN, BalanceConfig.PITCH_MAX);
        }

        adsPressed = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);

        boolean fireDown = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        Weapon currentWep = world.getWeaponSystem().getActiveWeapon();
        boolean auto = currentWep != null && currentWep.isAutomatic();
        firePressed = auto ? fireDown : (fireDown && !fireWasPressed);
        fireWasPressed = fireDown;

        // Feed aim direction into player for zombie targeting
        float cosYaw = MathUtils.cosDeg(yaw);
        float sinYaw = MathUtils.sinDeg(yaw);
        world.getPlayer().setAimDirection(sinYaw, cosYaw);
    }

    // ═════════════════════════════════════════════════════════
    //  CAMERA
    // ═════════════════════════════════════════════════════════

    private void updateCamera(float dt) {
        Player player = world.getPlayer();

        camera.position.set(
                player.getPosition().x,
                BalanceConfig.PLAYER_HEIGHT,
                player.getPosition().y
        );

        float cosYaw = MathUtils.cosDeg(yaw);
        float sinYaw = MathUtils.sinDeg(yaw);
        float cosPitch = MathUtils.cosDeg(pitch);
        float sinPitch = MathUtils.sinDeg(pitch);

        camera.direction.set(
                sinYaw * cosPitch,
                sinPitch,
                cosYaw * cosPitch
        ).nor();

        camera.up.set(0f, 1f, 0f);

        float targetFov = adsPressed ? BalanceConfig.FOV_ADS : BalanceConfig.FOV_DEFAULT;
        fovCurrent += (targetFov - fovCurrent) * BalanceConfig.ADS_INTERP_SPEED * dt;
        camera.fieldOfView = fovCurrent;

        camera.update();
    }

    // ═════════════════════════════════════════════════════════
    //  3D RENDERING
    // ═════════════════════════════════════════════════════════

    private void renderZombies() {
        for (Zombie z : world.getZombies()) {
            if (!z.isActive()) continue;

            zombieVisual.transform.setToTranslation(
                    z.getPosition().x, 0f, z.getPosition().y);

            Material mat = zombieVisual.materials.get(0);
            mat.set(ColorAttribute.createDiffuse(
                    z.getHitFlashTimer() > 0f ? COLOR_HIT : COLOR_ZOMBIE));

            modelBatch.render(zombieVisual, environment);
        }
    }

    // ═════════════════════════════════════════════════════════
    //  2D HUD
    // ═════════════════════════════════════════════════════════

    private void drawDamageVignette() {
        float alpha = gameHud.getDamageAlpha();
        if (alpha > 0f) {
            shapes.setColor(1f, 0f, 0f, alpha * 0.5f);
            shapes.rect(0f, 0f, screenWidth, screenHeight);
        }
    }

    private void drawHUDText() {
        Player player = world.getPlayer();
        WeaponSystem ws = world.getWeaponSystem();
        RoundManager rm = world.getRoundManager();
        Weapon wep = ws.getActiveWeapon();

        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, "HP: " + player.getHealth(), 12f, screenHeight - 12f);
        font.draw(batch, "Points: " + player.getPoints(), screenWidth - 160f, screenHeight - 12f);
        font.draw(batch, "Round: " + rm.getRound(), screenWidth / 2f - 40f, screenHeight - 12f);

        if (wep != null) {
            String ammoText = wep.getCurrentMag() + " / " + wep.getCurrentReserve();
            if (ws.isReloading()) ammoText += " [RELOADING]";
            font.draw(batch, ammoText, screenWidth / 2f - 30f, 40f);
            font.draw(batch, wep.getName(), screenWidth / 2f - 30f, 60f);
        }

        font.draw(batch, "Zombies: " + rm.getZombiesAlive(), 12f, 40f);
        font.draw(batch, "Kills: " + player.getZombieKills(), 12f, 60f);

        // Interact prompt
        String prompt = gameHud.getInteractPrompt();
        if (!prompt.isEmpty()) {
            font.setColor(0f, 1f, 0f, 1f);
            font.draw(batch, "[F] " + prompt, screenWidth / 2f - 60f, screenHeight / 2f + 20f);
            font.setColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawRoundAnnouncement() {
        if (!gameHud.isShowRound()) return;

        float alpha;
        float timer = gameHud.getRoundTimer();
        if      (timer > 2.5f) alpha = (3.0f - timer) / 0.5f;
        else if (timer < 0.5f) alpha = timer / 0.5f;
        else                    alpha = 1f;
        alpha = MathUtils.clamp(alpha, 0f, 1f);

        if (alpha > 0f) {
            font.getData().setScale(3f);
            font.setColor(0.8f, 0f, 0f, alpha);
            layout.setText(font, gameHud.getRoundText());
            font.draw(batch, gameHud.getRoundText(),
                    (screenWidth - layout.width) / 2f, screenHeight / 2f);
            font.getData().setScale(1.2f);

            font.getData().setScale(1.5f);
            font.setColor(0.6f, 0.6f, 0.6f, alpha * 0.7f);
            layout.setText(font, "Zombies incoming!");
            font.draw(batch, "Zombies incoming!",
                    (screenWidth - layout.width) / 2f, screenHeight / 2f - 40f);
            font.getData().setScale(1.2f);
        }
    }

    private void renderGameOver() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.7f);
        shapes.rect(0, 0, screenWidth, screenHeight);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        font.getData().setScale(2.5f);
        font.setColor(1f, 0f, 0f, 1f);
        layout.setText(font, "GAME OVER");
        font.draw(batch, "GAME OVER", (screenWidth - layout.width) / 2f, screenHeight / 2f + 20f);

        font.getData().setScale(1.2f);
        font.setColor(1f, 1f, 1f, 1f);
        layout.setText(font, "Press SPACE to restart");
        font.draw(batch, "Press SPACE to restart",
                (screenWidth - layout.width) / 2f, screenHeight / 2f - 20f);
        font.getData().setScale(1.2f);
        batch.end();
    }

    private void resetGame() {
        gameOver = false;
        moveX = 0f;
        moveY = 0f;
        firePressed = false;
        fireWasPressed = false;
        yaw = 0f;
        pitch = 0f;
        fovCurrent = BalanceConfig.FOV_DEFAULT;
        world.reset();
        rebuildWalls();
    }
}
