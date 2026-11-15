package com.example.lightningstrike;

import java.util.Optional;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Frozen Heart (levels I and II): when you hit something with a normal sword attack there is a chance
 * to slow it for 5 seconds. Level I = Slowness I with a 50% chance, level II = Slowness II with a 60% chance.
 * Lightning Strike beams do not trigger it (only normal melee hits do).
 */
public class FrozenHeartAbility {
	public static final ResourceKey<Enchantment> FROZEN_HEART = ResourceKey.create(
			Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(LightningStrikeMod.MOD_ID, "frozen_heart"));

	// ---- Tweak these numbers if you want ----
	private static final int SLOW_TICKS = 100; // 5 seconds

	/** Chance per hit: I = 50%, II = 60% */
	private static float chance(int level) {
		return 0.4f + 0.1f * level;
	}

	/** Slowness level: I = Slowness I, II = Slowness II (the game counts amplifier from 0) */
	private static int amplifier(int level) {
		return Math.min(level, 2) - 1;
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			// Only normal player melee hits, not Lightning Strike (that one is magic damage)
			if (blocked || !source.is(DamageTypes.PLAYER_ATTACK)
					|| !(source.getEntity() instanceof ServerPlayer player) || !entity.isAlive()) {
				return;
			}
			ItemStack stack = player.getMainHandItem();
			if (!stack.is(ItemTags.SWORDS)) {
				return;
			}
			int level = getLevel((ServerLevel) player.level(), stack);
			if (level <= 0 || player.getRandom().nextFloat() >= chance(level)) {
				return;
			}
			entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, amplifier(level)));
		});
	}

	private static int getLevel(ServerLevel server, ItemStack stack) {
		Optional<? extends Holder<Enchantment>> holder =
				server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(FROZEN_HEART);
		if (holder.isEmpty()) {
			return 0;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(holder.get(), stack);
	}
}
