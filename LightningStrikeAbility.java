package com.example.lightningstrike;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LightningStrikeAbility {
	public static final ResourceKey<Enchantment> LIGHTNING_STRIKE = ResourceKey.create(
			Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(LightningStrikeMod.MOD_ID, "lightning_strike"));

	private static final double RANGE = 10.0;
	private static final int COOLDOWN_TICKS = 200; // 10 seconds
	private static final float DAMAGE = 8.0f;
	private static final int YELLOW = 0xFFE600;

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(ItemTags.SWORDS) || !hasEnchant(server, stack)) {
				return InteractionResult.PASS;
			}
			if (player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.PASS;
			}

			fire(server, player);
			player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
			return InteractionResult.SUCCESS;
		});
	}

	private static boolean hasEnchant(ServerLevel server, ItemStack stack) {
		Optional<? extends Holder<Enchantment>> holder =
				server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(LIGHTNING_STRIKE);
		return holder.isPresent() && EnchantmentHelper.getItemEnchantmentLevel(holder.get(), stack) > 0;
	}

	private static void fire(ServerLevel server, Player player) {
		Vec3 start = player.getEyePosition().add(0, -0.15, 0);
		Vec3 dir = player.getLookAngle().normalize();
		Vec3 end = start.add(dir.scale(RANGE));

		// Stop the beam at solid blocks
		var blockHit = server.clip(new ClipContext(start, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double length = RANGE;
		if (blockHit.getType() != HitResult.Type.MISS) {
			length = Math.min(RANGE, blockHit.getLocation().distanceTo(start));
		}

		DustParticleOptions yellow = new DustParticleOptions(YELLOW, 1.0f);
		Set<LivingEntity> hit = new HashSet<>();

		for (double d = 0.8; d <= length; d += 0.3) {
			Vec3 p = start.add(dir.scale(d));
			server.sendParticles(yellow, p.x, p.y, p.z, 1, 0, 0, 0, 0);

			AABB box = new AABB(p, p).inflate(0.6);
			for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box,
					t -> t != player && t.isAlive() && !t.isSpectator()
							&& (t instanceof Enemy || t instanceof Player))) {
				if (hit.add(e)) {
					e.hurtServer(server, server.damageSources().indirectMagic(player, player), DAMAGE);
				}
			}
		}

		server.playSound(null, player.blockPosition(),
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.6f);
	}
}
