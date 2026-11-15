# Lightning Strike + Rage + Frozen Heart (Fabric, Minecraft 26.3)

Three sword enchantments.

**Lightning Strike (I-V)**: left-click with an enchanted sword. Yellow beams hit hostile mobs and other
players in range (one beam per target, nearest first, 4 hearts each). Level N gives 2N beams and
10 + 5(N-1) blocks of range. The higher the level, the shorter the cooldown:
I = 10s, II = 8.75s, III = 7.5s, IV = 6.25s, V = 5s.

**Rage (I-X)**: bonus melee damage while holding the sword.
- Alone: +0.5 hearts at I up to +1.4 at X.
- With Lightning Strike on the same sword: every enemy a strike hits adds +0.5 (I) to +0.95 (X) hearts,
  up to a cap of 3.0 (I) to 4.8 (X) hearts. The bonus is lost after 5 seconds out of combat.
- Levels I-V come from the enchanting table (VI and above are blocked there). Levels I-VI also appear in
  dungeon, mineshaft, desert pyramid and jungle temple chests (4%). Levels VII-X only appear in
  ancient city chests (12%). Two equal books can still be combined in an anvil to go one level higher.

**Frozen Heart (I-II)**: a normal sword hit has a chance to slow the target for 5 seconds.
Level I = Slowness I with a 50% chance, level II = Slowness II with a 60% chance.
Lightning Strike beams do not trigger it. Found in the enchanting table and random chest loot.

Build: `./gradlew build` -> `build/libs/lightning-strike-1.0.0.jar`
Test in dev: `./gradlew runClient`
/give @s diamond_sword[enchantments={"lightningstrike:rage":10,"lightningstrike:lightning_strike":5,"lightningstrike:frozen_heart":2}]
