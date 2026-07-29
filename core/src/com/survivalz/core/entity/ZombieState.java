package com.survivalz.core.entity;

/**
 * Contract for all zombie behaviors (State pattern).
 */
public interface ZombieState {
    void enter(Zombie z);
    void update(Zombie z, float deltaTime);
    void exit(Zombie z);
}
