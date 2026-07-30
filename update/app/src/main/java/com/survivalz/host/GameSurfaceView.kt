package com.survivalz.host

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.survivalz.core.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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

    private val holder = getHolder().apply { addCallback(this@GameSurfaceView) }
    private val renderThread = RenderThread()

    // Core world & loop
    private val world = GameWorld().apply {
        val pts = arrayListOf<RoundManager.SpawnPoint>()
        pts.add(RoundManager.SpawnPoint(-10f, -10f))
        pts.add(RoundManager.SpawnPoint(10f, -10f))
        pts.add(RoundManager.SpawnPoint(-10f, 10f))
        pts.add(RoundManager.SpawnPoint(10f, 10f))
        roundManager.setSpawnPoints(pts)
        // GameWorld already implements Killer and wires itself in its constructor
    }
    private val gameLoop = GameLoop(world, this, this)

    // Input state (polled on render thread, consumed on sim thread)
    private val inputState = AtomicReference(GameWorld.InputState())
    private var pendingInput = GameWorld.InputState()
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
                z.position.x - z.getRadius(), z.position.y - z.getRadius(),
                z.position.x + z.getRadius(), z.position.y + z.getRadius()
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
            debugRect.set(ia.x - 0.5f, ia.y - 0.5f, ia.x + 0.5f, ia.y + 0.5f)
            canvas.drawRect(debugRect, paint)
            canvas.drawText(ia.getPrompt(p), ia.x, ia.y - 0.7f, paint)
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

        // HUD (screen-space)
        canvas.save()
        canvas.translate(-cx, -cy)
        canvas.scale(1f / worldToScreen, -1f / worldToScreen)

        paint.color = 0xFFFFFFFF.toInt()
        paint.style = Paint.Style.FILL
        paint.textSize = 48f
        paint.strokeWidth = 0f

        canvas.drawText("Round: ${world.roundManager.round}", 40f, 80f, paint)
        canvas.drawText("Zombies: ${world.roundManager.zombiesAlive}", 40f, 160f, paint)
        canvas.drawText("Points: ${p.points}", 40f, 240f, paint)
        canvas.drawText("HP: ${p.health}", 40f, 320f, paint)

        val w = p.currentWeapon
        if (w != null) {
            canvas.drawText("${w.id} ${w.ammo}/${w.maxAmmo}", 40f, 400f, paint)
        }

        // Interaction prompt
        val hovered = world.hoveredInteractable
        if (hovered != null) {
            canvas.drawText(hovered.getPrompt(p), 40f, 480f, paint)
        }

        canvas.restore()
    }

    // Touch input
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex
        val ptrId = event.getPointerId(idx)

        when (action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ptrId < touchDown.size) {
                    touchDown[ptrId] = true
                    touchX[ptrId] = event.getX(idx)
                    touchY[ptrId] = event.getY(idx)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id < touchDown.size) {
                        touchX[id] = event.getX(i)
                        touchY[id] = event.getY(i)
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (ptrId < touchDown.size) {
                    touchDown[ptrId] = false
                }
            }
        }

        // Synthesize input state for this frame
        synthesizeInput()
        return true
    }

    private fun synthesizeInput() {
        val input = pendingInput

        // Left half = move joystick, right half = aim joystick
        var bestMovePtr = -1
        var bestAimPtr = -1
        var bestMoveDist = Float.MAX_VALUE
        var bestAimDist = Float.MAX_VALUE
        val cx = screenW / 2f
        val cy = screenH / 2f

        for (id in 0 until touchDown.size) {
            if (!touchDown[id]) continue
            val dx = touchX[id] - cx
            val dy = touchY[id] - cy
            val dist2 = dx * dx + dy * dy
            if (touchX[id] < cx) {
                if (dist2 < bestMoveDist) { bestMoveDist = dist2; bestMovePtr = id }
            } else {
                if (dist2 < bestAimDist) { bestAimDist = dist2; bestAimPtr = id }
            }
        }

        if (bestMovePtr != -1) {
            val dx = touchX[bestMovePtr] - cx
            val dy = touchY[bestMovePtr] - cy
            val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (len > 20f) {
                input.moveX = dx / len
                input.moveY = dy / len
            }
        }

        if (bestAimPtr != -1) {
            val dx = touchX[bestAimPtr] - cx
            val dy = touchY[bestAimPtr] - cy
            val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (len > 20f) {
                input.aimX = dx / len
                input.aimY = dy / len
            }
        }

        // Fire if any touch on right half
        if (bestAimPtr != -1) input.firing = true

        // Interact button: double-tap on left half (simplified: tap & hold > 0.3s not implemented here)
        // For now: tap interactable prompt area would be better, but skip for brevity
    }

    private inner class RenderThread : Thread() {
        private var running = true
        private var lastTime = System.nanoTime()

        override fun run() {
            while (running) {
                val now = System.nanoTime()
                val dt = ((now - lastTime) / 1_000_000_000.0).toFloat()
                lastTime = now

                if (dt > 0.25) continue // spiral of death guard

                gameLoop.tick(dt)

                // Frame pacing ~60fps
                val frameTime = System.nanoTime() - now
                val sleepTime = max(0L, (16_666_666L - frameTime) / 1_000_000)
                if (sleepTime > 0) Thread.sleep(sleepTime)
            }
        }

        fun requestStop() { running = false }
    }

    companion object {
        private const val TAG = "GameSurfaceView"
    }
}