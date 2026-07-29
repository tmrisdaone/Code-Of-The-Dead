package com.survivalz.core;

public interface Interactable {
    boolean canInteract(Player player);
    void onInteract(Player player);
    String getPrompt(Player player);
    float getX();
    float getY();
}
