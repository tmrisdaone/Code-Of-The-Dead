import java.util.ArrayList;
import java.util.List;

public class MysteryBox implements Interactable {
    private final float x, y;
    private final List<String> lootTable;
    private boolean inUse = false;
    private float cycleTimer = 0f;

    public MysteryBox(float x, float y, List<String> lootTable) {
        this.x = x; this.y = y;
        this.lootTable = new ArrayList<>(lootTable);
    }

    @Override
    public boolean canInteract(Player player) {
        if (inUse) return false;
        return player.getPosition().dist2(x, y) <= Player.INTERACT_RADIUS * Player.INTERACT_RADIUS;
    }

    @Override
    public void onInteract(Player player) {
        if (player.spendPoints(950)) {
            inUse = true;
            cycleTimer = 3.0f; // $$3$$ second roulette
        }
    }

    /** Call this from GameWorld.update so the box ticks even when the player stands still. */
    public void update(float deltaTime) {
        if (!inUse) return;
        cycleTimer -= deltaTime;
        if (cycleTimer <= 0f) {
            inUse = false;
            // Emit event: a random weapon from lootTable is now available for pickup
        }
    }

    @Override
    public String getPrompt(Player player) {
        return inUse ? "Rolling..." : "Use Mystery Box [950]";
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
}

/** Dropped by killed zombies; expires after a short time. */
public class PowerUp {
    public enum Type {
        INSTAKILL(30f), DOUBLE_POINTS(30f),
        MAX_AMMO(0f), NUKE(0f), CARPENTER(0f);

        final float duration;
        Type(float d) { this.duration = d; }
    }

    private final Vector2 position = new Vector2();
    private final Type type;
    private float lifetime = 20f; // disappears after $$20$$ s

    public PowerUp(float x, float y, Type type) {
        this.position.set(x, y);
        this.type = type;
    }

    public void update(float deltaTime) {
        lifetime -= deltaTime;
    }

    public boolean isExpired() { return lifetime <= 0f; }

    public void apply(Player player, RoundManager rm) {
        switch (type) {
            case INSTAKILL:
                player.activateBuff(Player.Buff.INSTAKILL, type.duration);
                break;
            case DOUBLE_POINTS:
                player.activateBuff(Player.Buff.DOUBLE_POINTS, type.duration);
                break;
            case MAX_AMMO:
                player.maxAmmoAllWeapons();
                break;
            case NUKE:
                rm.killAllActiveZombies(); // You would add this helper to RoundManager
                player.addPoints(400);
                break;
            case CARPENTER:
                // Repair all barricades in active zones
                break;
        }
    }

    public Vector2 getPosition() { return position; }
    public Type getType() { return type; }
}
