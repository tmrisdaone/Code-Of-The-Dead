package com.codzombies;

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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * Main game class — orchestrates all subsystems in a decoupled update/render loop.
 * Targets 60 FPS with delta-time frame independence.
 * Implements InputProcessor for touch and mouse input.
 *
 * Zero object allocation in update() and render().
 * All wall/floor/zombie models pre-built in create().
 */
public class CodZombiesGame extends ApplicationAdapter implements InputProcessor {

    // ── Core subsystems ──────────────────────────────────────
    private PlayerController player;
    private WeaponSystem     weaponSystem;
    private ZombieManager    zombieManager;
    private MapManager       mapManager;
    private GameHUD          hud;

    // ── 3D rendering ────────────────────────────────────────
    private ModelBatch      modelBatch;
    private Environment     environment;
    private ShapeRenderer   shapes;         // debug wireframe only
    private ModelInstance[] wallInstances;  // pre-built wall boxes
    private ModelInstance   floorInstance;
    private ModelInstance   zombieVisual;
    private Model           zombieModel;
    private Model           floorModel;

    // Pre-built wall colors (static — no alloc per frame)
    private static final Color COLOR_WALL    = new Color(0.40f, 0.32f, 0.22f, 1f);
    private static final Color COLOR_BARRIER = new Color(0.50f, 0.30f, 0.10f, 1f);
    private static final Color COLOR_DOOR    = new Color(0.20f, 0.50f, 0.20f, 1f);
    private static final Color COLOR_WALLBUY = new Color(0.80f, 0.80f, 0.00f, 1f);
    private static final Color COLOR_ZOMBIE  = new Color(0.60f, 0.00f, 0.00f, 1f);
    private static final Color COLOR_HIT     = new Color(1.00f, 1.00f, 1.00f, 1f);

    // ── Game state ──────────────────────────────────────────
    private boolean gameOver  = false;
    private boolean fireWasPressed = false;

    // ── Debug ───────────────────────────────────────────────
    public static boolean DEBUG_DRAW = false;

    @Override
    public void create() {
        // ── Subsystems ──────────────────────────────────────
        player = new PlayerController();
        player.position.set(6f * Constants.TILE_SIZE, 0f, 6f * Constants.TILE_SIZE);

        weaponSystem = new WeaponSystem(player);
        player.weaponSystem = weaponSystem;

        zombieManager = new ZombieManager(player);
        mapManager    = new MapManager(player);
        hud = new GameHUD(player, weaponSystem, zombieManager, mapManager);

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
        float mapWorld = MapManager.MAP_SIZE * Constants.TILE_SIZE;
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

        // ── Pre-build wall models ───────────────────────────
        int[][] map = mapManager.getMapData();
        int wallCount = 0;
        for (int y = 0; y < MapManager.MAP_SIZE; y++)
            for (int x = 0; x < MapManager.MAP_SIZE; x++)
                if (map[y][x] != MapManager.TILE_EMPTY) wallCount++;

        wallInstances = new ModelInstance[wallCount];
        int wi = 0;
        for (int y = 0; y < MapManager.MAP_SIZE; y++) {
            for (int x = 0; x < MapManager.MAP_SIZE; x++) {
                int tile = map[y][x];
                if (tile == MapManager.TILE_EMPTY) continue;

                Color c;
                switch (tile) {
                    case MapManager.TILE_WALL:    c = COLOR_WALL;    break;
                    case MapManager.TILE_BARRIER: c = COLOR_BARRIER; break;
                    case MapManager.TILE_DOOR:    c = COLOR_DOOR;    break;
                    case MapManager.TILE_WALLBUY: c = COLOR_WALLBUY; break;
                    default:                      c = COLOR_WALL;
                }
                Model box = mb.createBox(
                        Constants.TILE_SIZE, Constants.TILE_SIZE, Constants.TILE_SIZE,
                        new Material(ColorAttribute.createDiffuse(c)),
                        VertexAttributes.Usage.Position
                                | VertexAttributes.Usage.Normal
                );
                wallInstances[wi] = new ModelInstance(box);
                wallInstances[wi].transform.setToTranslation(
                        x * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f,
                        Constants.TILE_SIZE / 2f,
                        y * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f
                );
                wi++;
            }
        }

        // ── Zombie model ────────────────────────────────────
        zombieModel = mb.createBox(
                0.6f, 1.6f, 0.6f,
                new Material(ColorAttribute.createDiffuse(COLOR_ZOMBIE)),
                VertexAttributes.Usage.Position
                        | VertexAttributes.Usage.Normal
        );
        zombieVisual = new ModelInstance(zombieModel);

        // ── Input ───────────────────────────────────────────
        Gdx.input.setInputProcessor(this);
        Gdx.input.setCatchKey(Input.Keys.ESCAPE, true);

        zombieManager.signalNextRound();
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), Constants.DELTA_MAX);

        handleDesktopInput(dt);

        if (!gameOver) {
            player.update(dt);
            resolveCollision();

            weaponSystem.update(dt, zombieManager);
            zombieManager.update(dt);
            mapManager.update(dt, zombieManager);

            if (zombieManager.isRoundTransition())
                hud.showRoundAnnouncement(zombieManager.getCurrentRound());

            if (player.health < Constants.PLAYER_HEALTH_MAX * 0.3f)
                hud.triggerDamageFlash();

            if (!player.isAlive()) gameOver = true;
        }

        // ── Clear & render 3D scene ─────────────────────────
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        modelBatch.begin(player.camera);
        modelBatch.render(floorInstance, environment);

        // All walls (pre-built — zero allocation)
        for (ModelInstance inst : wallInstances)
            modelBatch.render(inst, environment);

        // Zombies
        renderZombies();
        modelBatch.end();

        // Debug wireframe overlay
        if (DEBUG_DRAW) {
            shapes.setProjectionMatrix(player.camera.combined);
            shapes.begin(ShapeRenderer.ShapeType.Line);
            renderDebugMap();
            shapes.end();
        }

        // ── 2D HUD ──────────────────────────────────────────
        hud.render(dt);

        if (gameOver) renderGameOver();
    }

    @Override
    public void resize(int width, int height) {
        player.camera.viewportWidth  = width;
        player.camera.viewportHeight = height;
        player.camera.update();
        hud.resize(width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        floorModel.dispose();
        zombieModel.dispose();
        // Dispose each wall model (first instance's model)
        for (ModelInstance inst : wallInstances)
            inst.model.dispose();
        shapes.dispose();
        hud.dispose();
    }

    // ═════════════════════════════════════════════════════════
    //  INPUT PROCESSOR
    // ═════════════════════════════════════════════════════════

    @Override public boolean touchDown(int sx, int sy, int ptr, int btn) { return hud.touchDown(sx, sy, ptr, btn); }
    @Override public boolean touchDragged(int sx, int sy, int ptr) { return hud.touchDragged(sx, sy, ptr); }
    @Override public boolean touchUp(int sx, int sy, int ptr, int btn) { return hud.touchUp(sx, sy, ptr); }
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
        if (keycode == Input.Keys.R)          { player.reloadPressed = true; return true; }
        if (keycode == Input.Keys.Q || keycode == Input.Keys.E) { weaponSystem.switchWeapon(); return true; }
        if (keycode == Input.Keys.F)          { hud.handleInteract(); return true; }
        if (keycode == Input.Keys.SPACE && gameOver) { resetGame(); return true; }
        return false;
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char c)   { return false; }
    @Override public boolean touchCancelled(int sx, int sy, int ptr, int btn) { return false; }

    // ═════════════════════════════════════════════════════════
    //  DESKTOP INPUT
    // ═════════════════════════════════════════════════════════

    private void handleDesktopInput(float dt) {
        float mx = 0f, my = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) my = 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) my = -1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) mx = -1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) mx = 1f;
        player.moveX = mx; player.moveY = my;

        if (Gdx.input.isCursorCatched()) {
            player.lookX =  Gdx.input.getDeltaX() * Constants.MOUSE_SENSITIVITY;
            player.lookY = -Gdx.input.getDeltaY() * Constants.MOUSE_SENSITIVITY;
        }

        boolean fireDown = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        Weapon wep = weaponSystem.getActiveWeapon();
        if (wep != null && wep.isAutomatic) {
            player.firePressed = fireDown;
        } else {
            player.firePressed = fireDown && !fireWasPressed;
        }
        fireWasPressed = fireDown;

        player.adsPressed = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
    }

    // ═════════════════════════════════════════════════════════
    //  COLLISION
    // ═════════════════════════════════════════════════════════

    private void resolveCollision() {
        Vector3 pos = player.position;
        float m = 0.3f;
        if (mapManager.checkCollision(pos.x - m, pos.z - m)) pos.x += 0.1f;
        if (mapManager.checkCollision(pos.x + m, pos.z - m)) pos.x -= 0.1f;
        if (mapManager.checkCollision(pos.x - m, pos.z + m)) pos.z += 0.1f;
        if (mapManager.checkCollision(pos.x + m, pos.z + m)) pos.z -= 0.1f;
        if (mapManager.checkCollision(pos.x, pos.z - m))     pos.z += 0.1f;
        if (mapManager.checkCollision(pos.x, pos.z + m))     pos.z -= 0.1f;
    }

    // ═════════════════════════════════════════════════════════
    //  3D RENDERING
    // ═════════════════════════════════════════════════════════

    private void renderZombies() {
        zombieManager.getZombiePool().forEachActive((zom, idx) -> {
            if (!zom.isActive() || zom.isDead()) return;

            zombieVisual.transform.setToTranslation(
                    zom.position.x, 0f, zom.position.z);

            Material mat = zombieVisual.materials.get(0);
            mat.set(ColorAttribute.createDiffuse(
                    zom.hitFlashTimer > 0f ? COLOR_HIT : COLOR_ZOMBIE));

            modelBatch.render(zombieVisual, environment);
        });
    }

    private void renderDebugMap() {
        shapes.setColor(1f, 1f, 1f, 0.3f);
        int[][] map = mapManager.getMapData();
        for (int y = 0; y < MapManager.MAP_SIZE; y++) {
            for (int x = 0; x < MapManager.MAP_SIZE; x++) {
                if (map[y][x] != MapManager.TILE_EMPTY) {
                    float wx = x * Constants.TILE_SIZE;
                    float wz = y * Constants.TILE_SIZE;
                    shapes.box(wx, 0f, wz,
                            Constants.TILE_SIZE, Constants.TILE_SIZE, Constants.TILE_SIZE);
                }
            }
        }
    }

    private void renderGameOver() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.7f);
        shapes.rect(0, 0,
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void resetGame() {
        gameOver = false;
        player.reset();
        weaponSystem = new WeaponSystem(player);
        player.weaponSystem = weaponSystem;
        zombieManager.reset();
        hud = new GameHUD(player, weaponSystem, zombieManager, mapManager);
        zombieManager.signalNextRound();
    }
}
