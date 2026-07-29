package com.survivalz.core;

/** Contract for all zombie behaviors. */
public interface ZombieState {
    void enter(Zombie z);
    void update(Zombie z, float deltaTime);
    void exit(Zombie z);
}
