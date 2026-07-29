package com.survivalz.core;

/** Contract for the host platform to supply per-frame input state. */
public interface GameInputPoll {
    GameWorld.InputState poll();
}