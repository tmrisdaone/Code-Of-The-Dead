package com.codzombies;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * 2D HUD overlay rendering: ammo counter, points, health, round announcements,
 * virtual joystick areas, touch buttons, crosshair, damage vignette.
 *
 * All rendering uses pre-allocated ShapeRenderer and SpriteBatch —
 * no texture/object creation in render().
 */
public class GameHUD {

    // ── Rendering ────────────────────────────────────────────
    private final OrthographicCamera hudCam;
    private final SpriteBatch  batch;
    private final ShapeRenderer shapes;
    private final BitmapFont   font;

    // ── HUD dimensions (set from viewport) ───────────────────
    private float width;
    private float height;

    // ── Crosshair ────────────────────────────────────────────
    private static final float CROSSHAIR_SIZE  = 8f;
    private static final float CROSSHAIR_GAP   = 4f;

    // ── Round announcement ──────────────────────────────────
    private String roundText    = "";
    private float  roundTimer   = 0f;
    private boolean showRound   = false;

    // ── Damage vignette ─────────────────────────────────────
    private float damageAlpha   = 0f;

    // ── Joystick base positions ─────────────────────────────
    private static final float JOYSTICK_RADIUS       = 60f;
    private static final float JOYSTICK_KNOB_RADIUS  = 20f;
    private static final float JOYSTICK_MARGIN       = 80f;

    // ── Button layout ───────────────────────────────────────
    private static final float BUTTON_SIZE    = 54f;
    private static final float BUTTON_PAD     = 12f;

    // Right-side button regions (computed in resize)
    private float fireBtnX, fireBtnY;
    private float adsBtnX,  adsBtnY;
    private float reloadBtnX, reloadBtnY;
    private float switchBtnX, switchBtnY;
    private float interactBtnX, interactBtnY;

    // ── Touch input state (read by PlayerController) ─────────
    // These are set via the touch callback methods called from the game screen.
    public boolean touchLeftActive   = false;
    public float   touchLeftX        = 0f;
    public float   touchLeftY        = 0f;
    public boolean touchRightActive  = false;
    public float   touchRightX       = 0f;
    public float   touchRightY       = 0f;

    // Touch IDs for tracking
    private int leftTouchId  = -1;
    private int rightTouchId = -1;
    private int fireTouchId  = -1;
    private int adsTouchId   = -1;

    // ── References ───────────────────────────────────────────
    private final PlayerController player;
    private final WeaponSystem     weaponSystem;
    private final ZombieManager    zombieManager;
    private final MapManager       mapManager;

    public GameHUD(PlayerController player, WeaponSystem weaponSystem,
                   ZombieManager zombieManager, MapManager mapManager) {
        this.player       = player;
        this.weaponSystem = weaponSystem;
        this.zombieManager = zombieManager;
        this.mapManager   = mapManager;

        hudCam = new OrthographicCamera();
        batch  = new SpriteBatch();
        shapes = new ShapeRenderer();
        font   = new BitmapFont();

        font.getData().setScale(1.2f);

        resize(Constants.VIEWPORT_WIDTH, Constants.VIEWPORT_HEIGHT);
    }

    public void resize(int w, int h) {
        width  = w;
        height = h;
        hudCam.setToOrtho(false, w, h);
        hudCam.update();

        // Compute button positions (right side)
        float rightEdge = w - BUTTON_PAD;
        float bottom    = BUTTON_PAD;

        reloadBtnX = rightEdge - BUTTON_SIZE - BUTTON_PAD;
        reloadBtnY = bottom + BUTTON_SIZE + BUTTON_PAD;
        switchBtnX = reloadBtnX - BUTTON_SIZE - BUTTON_PAD;
        switchBtnY = reloadBtnY;

        fireBtnX = rightEdge;
        fireBtnY = bottom;
        adsBtnX  = rightEdge - BUTTON_SIZE - BUTTON_PAD;
        adsBtnY  = bottom;

        interactBtnX = rightEdge;
        interactBtnY = bottom + BUTTON_SIZE + BUTTON_PAD;
    }

    // ── Main render ──────────────────────────────────────────
    public void render(float dt) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        hudCam.update();
        batch.setProjectionMatrix(hudCam.combined);
        shapes.setProjectionMatrix(hudCam.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawJoysticks();
        drawButtons();
        drawCrosshair();
        drawDamageVignette(dt);
        shapes.end();

        batch.begin();
        drawHUDText();
        drawRoundAnnouncement(dt);
        batch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // ── Touch callbacks (called from GameScreen) ────────────

    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        float x = screenX;
        float y = screenY; // already bottom-up from LibGDX

        // Left joystick
        if (x < width / 2f) {
            leftTouchId     = pointer;
            touchLeftActive = true;
            touchLeftX      = x;
            touchLeftY      = y;
            updateLeftStick(x, y);
            return true;
        }

        // Right side: check buttons
        if (inRect(x, y, fireBtnX, fireBtnY, BUTTON_SIZE, BUTTON_SIZE)) {
            fireTouchId = pointer;
            player.firePressed = true;
            return true;
        }
        if (inRect(x, y, adsBtnX, adsBtnY, BUTTON_SIZE, BUTTON_SIZE)) {
            adsTouchId = pointer;
            player.adsPressed = true;
            return true;
        }
        if (inRect(x, y, reloadBtnX, reloadBtnY, BUTTON_SIZE, BUTTON_SIZE)) {
            player.reloadPressed = true;
            return true;
        }
        if (inRect(x, y, switchBtnX, switchBtnY, BUTTON_SIZE, BUTTON_SIZE)) {
            weaponSystem.switchWeapon();
            return true;
        }
        if (inRect(x, y, interactBtnX, interactBtnY, BUTTON_SIZE, BUTTON_SIZE)) {
            handleInteract();
            return true;
        }

        // Right drag area (camera look)
        rightTouchId  = pointer;
        touchRightActive = true;
        touchRightX = x;
        touchRightY = y;
        return true;
    }

    public boolean touchDragged(int screenX, int screenY, int pointer) {
        float x = screenX;
        float y = screenY;

        if (pointer == leftTouchId && touchLeftActive) {
            updateLeftStick(x, y);
            return true;
        }

        if (pointer == rightTouchId && touchRightActive) {
            float dx = x - touchRightX;
            float dy = y - touchRightY;
            player.lookX = dx;
            player.lookY = dy;
            touchRightX = x;
            touchRightY = y;
            return true;
        }

        // Fire auto-fire: if holding fire button and auto weapon
        if (pointer == fireTouchId) {
            Weapon wep = weaponSystem.getActiveWeapon();
            if (wep != null && wep.isAutomatic) {
                player.firePressed = true;
            }
            return true;
        }

        return false;
    }

    public boolean touchUp(int screenX, int screenY, int pointer) {
        if (pointer == leftTouchId) {
            leftTouchId     = -1;
            touchLeftActive = false;
            player.moveX = 0f;
            player.moveY = 0f;
            return true;
        }
        if (pointer == rightTouchId) {
            rightTouchId      = -1;
            touchRightActive  = false;
            return true;
        }
        if (pointer == fireTouchId) {
            fireTouchId = -1;
            player.firePressed = false;
            return true;
        }
        if (pointer == adsTouchId) {
            adsTouchId = -1;
            player.adsPressed = false;
            return true;
        }
        return false;
    }

    // ── Round announcement ──────────────────────────────────
    public void showRoundAnnouncement(int round) {
        roundText  = "ROUND " + round;
        roundTimer = 3.0f;
        showRound = true;
    }

    // ── Damage flash ─────────────────────────────────────────
    public void triggerDamageFlash() {
        damageAlpha = 0.6f;
    }

    // ── Private rendering ───────────────────────────────────

    private void drawJoysticks() {
        // Left joystick base
        float lx = JOYSTICK_MARGIN + JOYSTICK_RADIUS;
        float ly = JOYSTICK_MARGIN + JOYSTICK_RADIUS;

        shapes.setColor(0.2f, 0.2f, 0.2f, 0.4f);
        shapes.circle(lx, ly, JOYSTICK_RADIUS);

        if (touchLeftActive) {
            shapes.setColor(0.4f, 0.4f, 0.4f, 0.6f);
            shapes.circle(
                    lx + player.moveX * JOYSTICK_RADIUS * 0.5f,
                    ly + player.moveY * JOYSTICK_RADIUS * 0.5f,
                    JOYSTICK_KNOB_RADIUS
            );
        }

        // Right joystick area indicator (subtle)
        float rx = width - JOYSTICK_MARGIN - JOYSTICK_RADIUS;
        float ry = height - JOYSTICK_MARGIN - JOYSTICK_RADIUS;
        shapes.setColor(0.2f, 0.2f, 0.2f, 0.2f);
        shapes.circle(rx, ry, JOYSTICK_RADIUS);
    }

    private void drawButtons() {
        // Semi-transparent button backgrounds
        drawButton(fireBtnX, fireBtnY, BUTTON_SIZE, "FIRE",  1f, 0.2f, 0.2f);
        drawButton(adsBtnX,  adsBtnY,  BUTTON_SIZE, "ADS",   0.2f, 0.5f, 1f);
        drawButton(reloadBtnX, reloadBtnY, BUTTON_SIZE, "R",  0.5f, 0.5f, 0.5f);
        drawButton(switchBtnX, switchBtnY, BUTTON_SIZE, "SW", 0.5f, 0.3f, 0.1f);
        drawButton(interactBtnX, interactBtnY, BUTTON_SIZE, "I", 0.3f, 0.8f, 0.3f);
    }

    private void drawButton(float bx, float by, float size, String label,
                            float r, float g, float b) {
        shapes.setColor(r, g, b, 0.5f);
        shapes.rect(bx, by, size, size);

        // Button border
        shapes.set(ShapeRenderer.ShapeType.Line);
        shapes.setColor(r, g, b, 0.8f);
        shapes.rect(bx, by, size, size);
        shapes.set(ShapeRenderer.ShapeType.Filled);

        // Label
        batch.begin();
        font.setColor(1f, 1f, 1f, 0.9f);
        float textX = bx + size / 2f - font.getRegion().getRegionWidth() * 0.3f;
        float textY = by + size / 2f + font.getCapHeight() / 2f;
        font.draw(batch, label, textX, textY);
        batch.end();
    }

    private void drawCrosshair() {
        float cx = width / 2f;
        float cy = height / 2f;

        shapes.setColor(1f, 1f, 1f, 0.8f);
        // Top
        shapes.rect(cx - 1f, cy + CROSSHAIR_GAP, 2f, CROSSHAIR_SIZE);
        // Bottom
        shapes.rect(cx - 1f, cy - CROSSHAIR_GAP - CROSSHAIR_SIZE, 2f, CROSSHAIR_SIZE);
        // Left
        shapes.rect(cx - CROSSHAIR_GAP - CROSSHAIR_SIZE, cy - 1f, CROSSHAIR_SIZE, 2f);
        // Right
        shapes.rect(cx + CROSSHAIR_GAP, cy - 1f, CROSSHAIR_SIZE, 2f);
        // Center dot
        shapes.circle(cx, cy, 1.5f);
    }

    private void drawDamageVignette(float dt) {
        if (damageAlpha > 0f) {
            // Red vignette overlay
            shapes.setColor(1f, 0f, 0f, damageAlpha * 0.5f);
            shapes.rect(0f, 0f, width, height);
            damageAlpha -= dt * 2f;
            if (damageAlpha < 0f) damageAlpha = 0f;
        }
    }

    private void drawHUDText() {
        font.setColor(1f, 1f, 1f, 1f);

        // ── Health bar (top-left) ──
        float healthPct = player.health / player.healthMax;
        String healthColor = healthPct > 0.5f ? "[GREEN]" :
                healthPct > 0.25f ? "[YELLOW]" : "[RED]";
        font.draw(batch, "HP: " + (int)player.health, 12f, height - 12f);

        // ── Points (top-right) ──
        font.draw(batch, "Points: " + player.points, width - 160f, height - 12f);

        // ── Round (top-center) ──
        font.draw(batch, "Round: " + zombieManager.getCurrentRound(),
                width / 2f - 40f, height - 12f);

        // ── Ammo (bottom-center) ──
        Weapon wep = weaponSystem.getActiveWeapon();
        if (wep != null) {
            String ammoText = wep.currentMag + " / " + wep.currentReserve;
            // Reloading indicator
            if (weaponSystem.isReloading) {
                ammoText += " [RELOADING]";
            }
            font.draw(batch, ammoText, width / 2f - 30f, 40f);

            // Weapon name
            font.draw(batch, wep.name, width / 2f - 30f, 60f);
        }

        // ── Zombie counter ──
        String zombieText = "Zombies: " + zombieManager.getZombieCount();
        font.draw(batch, zombieText, 12f, 40f);

        // ── Kills ──
        font.draw(batch, "Kills: " + player.zombieKills, 12f, 60f);

        // ── Interaction prompt ──
        drawInteractionPrompts();
    }

    private void drawInteractionPrompts() {
        // Door prompt
        for (int i = 0; i < mapManager.doorCount; i++) {
            MapManager.Door d = mapManager.doors[i];
            if (d.isOpen) continue;
            float dx = (d.tilesX * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f) - player.position.x;
            float dz = (d.tilesY * Constants.TILE_SIZE + Constants.TILE_SIZE / 2f) - player.position.z;
            float dist2 = dx * dx + dz * dz;
            if (dist2 < 16f) {
                font.setColor(0.8f, 0.8f, 0f, 1f);
                font.draw(batch, "[I] Unlock $" + d.cost, width / 2f - 60f, height / 2f + 40f);
                font.setColor(1f, 1f, 1f, 1f);
            }
        }

        // Wall buy prompt
        int wbIdx = mapManager.getNearestWallBuyIndex();
        if (wbIdx >= 0) {
            MapManager.WallBuy wb = mapManager.wallBuys[wbIdx];
            font.setColor(0f, 1f, 0f, 1f);
            font.draw(batch, "[I] " + wb.getLabel(), width / 2f - 60f, height / 2f + 20f);
            font.setColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawRoundAnnouncement(float dt) {
        if (!showRound) return;

        roundTimer -= dt;

        // Fade-in / fade-out effect
        float alpha;
        if (roundTimer > 2.5f) {
            alpha = (3.0f - roundTimer) / 0.5f; // fade in
        } else if (roundTimer < 0.5f) {
            alpha = roundTimer / 0.5f; // fade out
        } else {
            alpha = 1f; // full visibility
        }

        alpha = MathUtils.clamp(alpha, 0f, 1f);

        if (alpha > 0f) {
            font.setColor(0.8f, 0f, 0f, alpha); // blood red
            font.getData().setScale(3f);
            float textWidth = font.getRegion().getRegionWidth();
            font.draw(batch, roundText,
                    width / 2f - textWidth * 1.5f, height / 2f);
            font.getData().setScale(1.2f);

            // Subtitle
            font.setColor(0.6f, 0.6f, 0.6f, alpha * 0.7f);
            font.getData().setScale(1.5f);
            font.draw(batch, "Zombies incoming!",
                    width / 2f - 70f, height / 2f - 40f);
            font.getData().setScale(1.2f);
        }

        if (roundTimer <= 0f) {
            showRound = false;
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private void updateLeftStick(float x, float y) {
        float dx = x - (JOYSTICK_MARGIN + JOYSTICK_RADIUS);
        float dy = y - (JOYSTICK_MARGIN + JOYSTICK_RADIUS);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxDist = JOYSTICK_RADIUS * 0.5f;

        if (dist > maxDist) {
            dx = (dx / dist) * maxDist;
            dy = (dy / dist) * maxDist;
        }

        player.moveX = dx / maxDist;
        player.moveY = dy / maxDist;
    }

    private boolean inRect(float x, float y, float rx, float ry,
                            float rw, float rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }

    public void handleInteract() {
        // Check wall buy first
        int wbIdx = mapManager.getNearestWallBuyIndex();
        if (wbIdx >= 0) {
            MapManager.WallBuy wb = mapManager.wallBuys[wbIdx];
            if (weaponSystem.buyWallWeapon(wb.weaponIndex)) {
                wb.purchased = true;
                return;
            }
        }
        // Fallback: doors, barriers
        mapManager.interact();
    }

    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
