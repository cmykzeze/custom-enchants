package com.example.lightningstrike;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LightningStrikeAbility {
	public static final ResourceKey<Enchantment> LIGHTNING_STRIKE = ResourceKey.create(
			Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(LightningStrikeMod.MOD_ID, "lightning_strike"));

	// ---- Tweak these numbers if you want ----
	private static final int COOLDOWN_TICKS_LEVEL_1 = 200; // 10 seconds at level I
	private static final int COOLDOWN_TICKS_LEVEL_5 = 100; // 5 seconds at level V (levels in between are evenly spaced)
	private static final float DAMAGE = 8.0f;            // 4 hearts per hit, every level
	private static final int BRANCHES_PER_LEVEL = 2;     // level I = 2 branches, V = 10
	private static final double BASE_RANGE = 10.0;       // level I = 10 blocks
	private static final double RANGE_PER_LEVEL = 5.0;   // +5 blocks per extra level (V = 30)
	private static final double STEP = 0.35;             // particle spacing
	private static final int YELLOW = 0xFFE600;

	/** Called on the server when a player left-clicks with a sword. */
	public static void activate(ServerPlayer player) {
		ServerLevel server = (ServerLevel) player.level();
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ItemTags.SWORDS)) {
			return;
		}
		int level = getLevel(server, stack);
		if (level <= 0 || player.getCooldowns().isOnCooldown(stack)) {
			return;
		}

		int hits = fire(server, player, level);
		RageAbility.onLightningStrike(server, player, stack, hits);
		player.getCooldowns().addCooldown(stack, cooldownTicks(level));
	}

	/** Higher level = shorter cooldown: I = 10s, II = 8.75s, III = 7.5s, IV = 6.25s, V = 5s. */
	private static int cooldownTicks(int level) {
		int l = Math.max(1, Math.min(5, level));
		return COOLDOWN_TICKS_LEVEL_1
				- (COOLDOWN_TICKS_LEVEL_1 - COOLDOWN_TICKS_LEVEL_5) * (l - 1) / 4;
	}

	private static int getLevel(ServerLevel server, ItemStack stack) {
		Optional<? extends Holder<Enchantment>> holder =
				server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(LIGHTNING_STRIKE);
		if (holder.isEmpty()) {
			return 0;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(holder.get(), stack);
	}

	/** Fires the strike and returns how many enemies it damaged. */
	private static int fire(ServerLevel server, ServerPlayer player, int level) {
		int branches = level * BRANCHES_PER_LEVEL;
		double range = BASE_RANGE + (level - 1) * RANGE_PER_LEVEL;

		Vec3 origin = player.getEyePosition().add(0, -0.15, 0);
		Vec3 look = player.getLookAngle().normalize();

		// Hostile mobs and other players inside the radius, nearest first
		List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(range),
				t -> t != player && t.isAlive() && !t.isSpectator()
						&& (t instanceof Enemy || t instanceof Player)
						&& t.distanceTo(player) <= range);
		targets.sort(Comparator.comparingDouble(t -> t.distanceToSqr(player)));

		DustParticleOptions yellow = new DustParticleOptions(YELLOW, 1.0f);
		int used = 0;
		int hits = 0;

		// One branch per target, nearest first (skip targets behind walls)
		for (LivingEntity t : targets) {
			if (used >= branches) {
				break;
			}
			Vec3 center = t.position().add(0, t.getBbHeight() * 0.5, 0);
			var los = server.clip(new ClipContext(origin, center,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (los.getType() != HitResult.Type.MISS) {
				continue;
			}
			// One branch = one target = one hit
			if (beam(server, player, origin, center.subtract(origin).normalize(),
					Math.min(range, origin.distanceTo(center)), yellow, t)) {
				hits++;
			}
			used++;
		}

		// Leftover branches (no target left) fan out in front of you, just for show
		RandomSource rnd = player.getRandom();
		for (; used < branches; used++) {
			Vec3 dir = (used == 0 ? look : look.add(
					(rnd.nextDouble() - 0.5) * 0.9,
					(rnd.nextDouble() - 0.5) * 0.5,
					(rnd.nextDouble() - 0.5) * 0.9)).normalize();
			beam(server, player, origin, dir, range, yellow, null);
		}

		server.playSound(null, player.blockPosition(),
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.6f);
		return hits;
	}

	/** Draws one straight yellow line. If it has a target, that target takes damage exactly once.
	 *  Returns true if a target was damaged. */
	private static boolean beam(ServerLevel server, ServerPlayer player, Vec3 start, Vec3 dir,
			double maxLength, DustParticleOptions particle, LivingEntity target) {
		double length = maxLength;
		var blockHit = server.clip(new ClipContext(start, start.add(dir.scale(maxLength)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			length = Math.min(maxLength, blockHit.getLocation().distanceTo(start));
		}

		for (double d = 0.8; d <= length; d += STEP) {
			Vec3 p = start.add(dir.scale(d));
			server.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}

		if (target != null && target.isAlive()) {
			return target.hurtServer(server, server.damageSources().indirectMagic(player, player), DAMAGE);
		}
		return false;
	}
}
