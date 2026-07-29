public class GameSurfaceView extends SurfaceView implements Runnable {
    private final GameWorld world = new GameWorld();
    private final GameRenderer renderer; // your custom Canvas or OpenGL renderer
    private Thread gameThread;
    private volatile boolean running = false;

    public void run() {
        long lastTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - lastTime) / 1_000_000_000f;
            lastTime = now;

            // Clamp $$dt$$ to avoid spiral-of-death if the app resumes from pause
            if (dt > 0.25f) dt = 0.25f;

            InputState in = pollTouchInput(); // populate from MotionEvents
            world.update(dt, in);

            Canvas c = holder.lockCanvas();
            renderer.render(world, c); // read-only render pass
            holder.unlockCanvasAndPost(c);
        }
    }
}
