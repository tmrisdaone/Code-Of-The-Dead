com.survivalz.core
  ├── GameWorld.java          (central model, holds all entities)
  ├── GameLoop.java           (fixed timestep)
  ├── EventBus.java           (Observer)
  ├── GameEvent.java          (event types)
  ├── math/Vec2.java          (mutable vector, pooled)
  ├── pool/ObjectPool.java
  ├── entity/Player.java
  ├── entity/PlayerController.java (input handling)
  ├── entity/Zombie.java
  ├── entity/ZombieState.java (enum-based state machine)
  ├── entity/ZombiePool/Spawner
  ├── weapon/Weapon.java, WeaponDef.java
  ├── interact/Interactable.java (interface)
  ├── interact/WallBuy.java
  ├── interact/Door.java
  ├── interact/MysteryBox.java
  ├── round/RoundManager.java
  ├── config/BalanceConfig.java (scaling formulas)
  └── render/GameRenderer.java (interface/adapter - just outline)
