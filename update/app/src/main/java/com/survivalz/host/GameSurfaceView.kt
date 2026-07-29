package com.survivalz.host

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.survivalz.core.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Android SurfaceView host that implements the core's GameInputPoll and GameRenderer
 * contracts. Owns the render thread and drives the fixed-timestep GameLoop.
 */
class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr),
    SurfaceHolder.Callback,
    GameInputPoll,
    GameRenderer {

    private val holder = holder.apply { addCallback(this) }
    private val renderThread = RenderThread()

    // Core world & loop
    private val world = GameWorld().apply {
        // Add some test spawn points around the center
        val pts = arrayListOf<RoundManager.SpawnPoint>()
        pts.add(RoundManager.SpawnPoint(-10f, -10f))
        pts.add(RoundManager.SpawnPoint(10f, -10f))
        pts.add(RoundManager.SpawnPoint(-10f, 10f))
        pts.add(RoundManager.SpawnPoint(10f, 10f))
        roundManager.setSpawnPoints(pts)
        roundManager.setKiller(this) // wire NUKE support
    }
    private val gameLoop = GameLoop(world, this, this)

    // Input state (polled on render thread, consumed on sim thread)
    private val inputState = AtomicReference(GameWorld.InputState())
    private val pendingInput = GameWorld.InputState()
    private val touchDown = BooleanArray(10)
    private val touchX = FloatArray(10)
    private val touchY = FloatArray(10)

    // Rendering
    private val paint = Paint().apply { isAntiAlias = true; textSize = 24f }
    private val debugRect = RectF()
    private var screenW = 0
    private var screenH = 0
    private var worldToScreen = 100f // pixels per world meter

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderThread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenW = width
        screenH = height
        worldToScreen = min(width, height) / 20f // ~20 world units visible
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        renderThread.requestStop()
        try { renderThread.join() } catch (_: InterruptedException) {}
    }

    // ===== GameInputPoll =====
    override fun poll(): GameWorld.InputState {
        // Atomically swap in the latest input gathered on the render thread
        val snapshot = inputState.getAndSet(pendingInput)
        pendingInput = snapshot // recycle object
        pendingInput.reset()
        return snapshot
    }

    // ===== GameRenderer =====
    override fun render(world: GameWorld) {
        val canvas = holder.lockCanvas() ?: return
        try {
            drawFrame(canvas, world)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawFrame(canvas: Canvas, world: GameWorld) {
        canvas.drawColor(0xFF111111.toInt())

        // World -> screen transform
        val cx = screenW / 2f
        val cy = screenH / 2f
        canvas.translate(cx, cy)
        canvas.scale(worldToScreen, -worldToScreen) // Y-up world

        // Draw zombies
        paint.color = 0xFFCC3333.toInt()
        paint.style = Paint.Style.FILL
        for (z in world.zombies) {
            if (!z.isActive()) continue
            debugRect.set(
                z.position.x - z.radius, z.position.y - z.radius,
                z.position.x + z.radius, z.position.y + z.radius
            )
            canvas.drawRect(debugRect, paint)
        }

        // Draw player
        val p = world.player
        paint.color = 0xFF33CC33.toInt()
        debugRect.set(
            p.position.x - Player.RADIUS, p.position.y - Player.RADIUS,
            p.position.x + Player.RADIUS, p.position.y + Player.RADIUS
        )
        canvas.drawRect(debugRect, paint)

        // Draw aim line
        paint.strokeWidth = 2f / worldToScreen
        paint.style = Paint.Style.STROKE
        paint.color = 0xFFFFFFFF.toInt()
        val aimLen = 2f
        canvas.drawLine(
            p.position.x, p.position.y,
            p.position.x + cos(p.aimAngle) * aimLen,
            p.position.y + sin(p.aimAngle) * aimLen,
            paint
        )

        // Draw interactables
        paint.strokeWidth = 3f / worldToScreen
        paint.color = 0xFFFFFF00.toInt()
        for (ia in world.interactables) {
            debugRect.set(ia.getX() - 0.5f, ia.getY() - 0.5f, ia.getX() + 0.5f, ia.getY() + 0.5f)
            canvas.drawRect(debugRect, paint)
            canvas.drawText(ia.getPrompt(p), ia.getX(), ia.getY() - 0.7f, paint)
        }

        // Draw power-ups
        for (pu in world.powerUps) {
            if (pu.isExpired()) continue
            paint.color = when (pu.type) {
                PowerUp.Type.INSTAKILL -> 0xFFFF0000.toInt()
                PowerUp.Type.DOUBLE_POINTS -> 0xFF00FFFF.toInt()
                PowerUp.Type.MAX_AMMO -> 0xFFFFFF00.toInt()
                PowerUp.Type.NUKE -> 0xFFFF00FF.toInt()
                PowerUp.Type.CARPENTER -> 0xFF00FF00.toInt()
            }
            debugRect.set(
                pu.position.x - 0.3f, pu.position.y - 0.3f,
                pu.position.x + 0.3f, pu.position.y + 0.3f
            )
            canvas.drawRect(debugRect, paint)
        }

        // Draw mystery box
        if (world.mysteryBox != null) {
            val mb = world.mysteryBox!!
            paint.color = if (mb.isInUse()) 0xFFFF8800.toInt() else 0xFF8888FF.toInt()
            debugRect.set(mb.getX() - 0.6f, mb.getY() - 0.6f, mb.getX() + 0.6f, mb.getY() + 0.6f)
            canvas.drawRect(debugRect, paint)
            canvas.drawText(mb.getPrompt(p), mb.getX(), mb.getY() - 0.9f, paint)
        }

        // HUD (draw in screen space)
        canvas.scale(1f / worldToScreen, -1f / worldToScreen)
        canvas.translate(-cx, -cy)
        paint.textSize = 28f
        paint.color = 0xFFFFFFFF.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawText("Round: ${world.roundManager.round}  Zombies: ${world.zombies.count { it.isActive() }}", 20f, 40f, paint)
        canvas.drawText("Points: ${p.points}  Ammo: ${p.getCurrentWeapon()?.ammo ?: 0}/${p.getCurrentWeapon()?.maxAmmo ?: 0}", 20f, 76f, paint)
        canvas.drawText("Health: ${p.health}/${Player.MAX_HEALTH}", 20f, 112f, paint)
    }

    // ===== Touch handling (render thread) =====
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex
        val id = event.getPointerId(idx)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (id < touchDown.size) {
                    touchDown[id] = true
                    touchX[id] = event.getX(idx)
                    touchY[id] = event.getY(idx)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (id < touchDown.size) touchDown[id] = false
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (pid < touchDown.size && touchDown[pid]) {
                        touchX[pid] = event.getX(i)
                        touchY[pid] = event.getY(i)
                    }
                }
            }
        }

        // Map first touch to move, second touch to aim, tap to interact
        var moveX = 0f; var moveY = 0f; var aimX = 0f; var aimY = 0f; var firing = false; var interact = false

        // Pointer 0: move (left side), Pointer 1: aim (right side)
        for (i in 0 until touchDown.size) {
            if (!touchDown[i]) continue
            val tx = (touchX[i] - screenW / 2f) / worldToScreen
            val ty = -(touchY[i] - screenH / 2f) / worldToScreen // flip Y

            if (i == 0 || touchX[i] < screenW / 2f) {
                moveX = tx; moveY = ty
            } else {
                aimX = tx; aimY = ty; firing = true
            }
        }

        // Quick tap on player = interact (simple heuristic)
        if (event.action == MotionEvent.ACTION_UP && event.pointerCount == 1) {
            interact = true
        }

        // Write into pending input (consumed by sim thread via poll())
        pendingInput.moveX = moveX.coerceIn(-1f, 1f)
        pendingInput.moveY = moveY.coerceIn(-1f, 1f)
        pendingInput.aimX = aimX
        pendingInput.aimY = aimY
        pendingInput.firing = firing
        pendingInput.interact = interact

        return true
    }

    private inner class RenderThread : Thread() {
        private var running = true
        private var lastTime = System.nanoTime()

        override fun run() {
            while (running) {
                val now = System.nanoTime()
                val dt = (now - lastTime) / 1e9f
                lastTime = now
                gameLoop.tick(dt)
            }
        }

        fun requestStop() { running = false }
    }

    companion object {
        private const val TAG = "GameSurfaceView"
    }
}