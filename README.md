# Code Of The Dead

A fully playable **Call of Duty Zombies** clone for Android built with **LibGDX** and **Java**, featuring a complete 3D first-person shooter engine with dual-stick touch controls, wave-based zombie combat, weapon progression, and map mechanics.

## Features

### Gameplay
- **Wave/Round System** — Zombies escalate per round (formula: `⌊0.24N² + 12N + 6⌋`) with health scaling (+10% per round past 9)
- **4 Core Weapons** — M1911 (starter), M14 (wall-buy), MP40 (wall-buy), STG-44 (wall-buy), Thompson (wall-buy), Ray Gun (splash damage)
- **Points Economy** — 10 pts/hit, 60 pts/kill, 100 pts/headshot
- **Zombie AI** — State machine: SPAWNING → PURSUING → ATTACKING → DEATH with 3D pursuit steering

### Map Mechanics
- **12×12 Tile Grid** with walls, doors, barricades, and wall-buy stations
- **Barricades** — Zombies must breach boards before entering; rebuild for +10 pts each
- **Doors** — Unlock new areas for 750/1000/1250 points
- **Wall Buys** — Purchase weapons from wall-mounted stations

### Controls (Mobile)
- **Dual Virtual Joysticks** — Left for movement (360° WASD-style), right-drag for camera look
- **Touch Buttons** — Fire, ADS (Aim Down Sights), Reload, Switch Weapon, Interact/Buy

### Controls (Desktop)
- **WASD** — Movement
- **Mouse** — Look (click screen to capture cursor, ESC to release)
- **LMB** — Fire / **RMB** — ADS
- **R** — Reload / **Q/E** — Switch Weapon / **F** — Interact
- **SPACE** — Restart on game over

## Project Structure

```
code-of-the-dead/
├── core/src/main/java/com/codzombies/    # Core game logic
│   ├── Constants.java         # All game formulas & tuning values
│   ├── ObjectPool.java        # Zero-GC object pool (Zombies, Bullets)
│   ├── PlayerController.java  # FPS camera, touch input, health regen, recoil
│   ├── Weapon.java            # Weapon definitions catalog (6 weapons)
│   ├── WeaponSystem.java      # Firing, reload, ADS, swap mechanics
│   ├── Zombie.java            # Zombie entity with state machine
│   ├── ZombieManager.java     # Wave spawning, AI, hit-scan, bullet pool
│   ├── Bullet.java            # Projectile entity (Ray Gun)
│   ├── MapManager.java        # Map grid, doors, barricades, wall buys
│   ├── GameHUD.java           # 2D overlay (joysticks, buttons, HUD text)
│   └── CodZombiesGame.java    # Main orchestrator & InputProcessor
├── android/                   # Android launcher (fullscreen, immersive)
├── desktop/                   # Desktop LWJGL3 launcher (PC testing)
└── build.gradle               # Multi-module Gradle build
```

## Technical Architecture

| System | Implementation |
|--------|---------------|
| **Rendering** | LibGDX 3D API (`ModelBatch`, `Environment`, `ModelInstance`) |
| **Camera** | PerspectiveCamera with pitch/yaw constraints (-89° to +89°) |
| **Lighting** | Ambient + directional lighting for dynamic scenes |
| **Performance** | Pre-built static geometry, object pooling, zero allocations in hot loops |
| **Memory** | All game entities pre-allocated via `ObjectPool<T>` — no GC hitches |
| **Input** | `InputProcessor` pattern with touch forwarding to HUD |

## Building

### Desktop (for testing)
```bash
./gradlew :desktop:run
```

### Android APK
```bash
./gradlew :android:assembleDebug
# APK at android/build/outputs/apk/debug/
```

### Prerequisites
- Java JDK 17+
- Android SDK (for Android build)
- Gradle (bundled wrapper)

## Requirements

- **Android:** API 24+ (Android 7.0+), OpenGL ES 3.0
- **Desktop:** Any system with LWJGL3 support
- **Storage:** ~50MB

## Credits

Built with [LibGDX](https://libgdx.com/) — cross-platform game development framework.

---

*"The dead rise again."*
