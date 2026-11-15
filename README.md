# Lightning Strike + Rage + Frozen Heart + Bleed (Fabric, Minecraft 26.3)

Four sword enchantments.

**Lightning Strike (I-V)**: left-click with an enchanted sword. Yellow beams hit hostile mobs and other
players in range (one beam per target, nearest first, 4 hearts each). If there is only one enemy in range,
all branches focus on it and the hit does 1.5x damage (6 hearts). Level N gives 2N beams and
10 + 5(N-1) blocks of range. The higher the level, the shorter the cooldown:
I = 10s, II = 8.75s, III = 7.5s, IV = 6.25s, V = 5s.

**Rage (I-VII)**: bonus melee damage while holding the sword.
- Alone: +0.4 hearts at I, +0.6 at V, +0.7 at VII.
- With Lightning Strike on the same sword: every enemy a strike hits adds +0.4 (I) to +0.56 (VII) hearts,
  up to a cap of 2.2 (I) to 3.1 (VII) hearts. The bonus is lost after 5 seconds out of combat.
- Levels I-V come from the enchanting table (VI and above are blocked there). Levels I-VI also appear in
  dungeon, mineshaft, desert pyramid and jungle temple chests (4%). Level VII only appears in
  ancient city chests (12%). Two level VI books can still be combined in an anvil to make VII.

**Frozen Heart (I-II)**: a normal sword hit has a chance to slow the target for 5 seconds.
Level I = Slowness I with a 50% chance, level II = Slowness II with a 60% chance.
Lightning Strike beams do not trigger it. Found in the enchanting table and random chest loot.

**Bleed (I-IV)**: a normal sword hit has a 20% / 25% / 30% / 35% chance to make the target bleed for 4 seconds.
- Blood pulses once per second (4 pulses), ignoring armor: 1 / 2 / 3 / 4 hearts in total.
- Blinds the target when the bleed starts: 1s at I-II, 2s at III-IV.
- While your target bleeds, your own melee damage is reduced by 15% / 25% / 35% / 40%.
- A new proc on a bleeding target refreshes it (no stacking). Lightning Strike beams do not trigger it.

Build: `./gradlew build` -> `build/libs/lightning-strike-1.0.0.jar`
Test in dev: `./gradlew runClient`
/give @s diamond_sword[enchantments={"lightningstrike:rage":7,"lightningstrike:lightning_strike":5,"lightningstrike:frozen_heart":2,"lightningstrike:bleed":4}]
