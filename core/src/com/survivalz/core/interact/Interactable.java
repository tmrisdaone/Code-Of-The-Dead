package com.survivalz.core.interact;

import com.survivalz.core.entity.Player;

/**
 * Contract for all world interactables: wall buys, doors, mystery boxes, etc.
 */
public interface Interactable {
    boolean canInteract(Player player);
    void onInteract(Player player);
    String getPrompt(Player player);
    float getX();
    float getY();
}
