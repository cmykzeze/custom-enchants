package com.example.lightningstrike;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Rage (levels I to X):
 *  - alone: bonus melee damage while holding the sword
 *  - with Lightning Strike: every enemy a Lightning Strike hits adds bonus damage (up to a cap).
 *    The bonus lasts while you are in combat and is lost after 5 seconds without combat.
 *  Numbers per level are in the three small methods below (hearts are converted to damage points).
 */
public class RageAbility {
	public static final ResourceKey<Enchantment> RAGE = ResourceKey.create(
			Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(LightningStrikeMod.MOD_ID, "rage"));

	private static final Identifier MODIFIER_ID =
			Identifier.fromNamespaceAndPath(LightningStrikeMod.MOD_ID, "rage_bonus");

	// ---- Tweak these numbers if you want ----
	private static final int COMBAT_TIMEOUT_TICKS = 100; // 5 seconds

	/** Bonus with Rage alone, in hearts: I = 0.5 ... X = 1.4 */
	private static double aloneHearts(int level) {
		return 0.5 + 0.1 * (level - 1);
	}

	/** Bonus per enemy hit by Lightning Strike, in hearts: I = 0.5 ... X = 0.95 */
	private static double perEnemyHearts(int level) {
		return 0.5 + 0.05 * (level - 1);
	}

	/** Maximum combo bonus, in hearts: I = 3.0 ... X = 4.8 */
	private static double capHearts(int level) {
		return 3.0 + 0.2 * (level - 1);
	}

	private static class State {
		double comboBonus = 0;
		int lastActiveTick = 0;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private static int getLevel(ServerLevel server, ItemStack stack, ResourceKey<Enchantment> key) {
		Optional<? extends Holder<Enchantment>> holder =
				server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
		if (holder.isEmpty()) {
			return 0;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(holder.get(), stack);
	}

	/** Called by Lightning Strike after a cast, with the number of enemies it hit. */
	public static void onLightningStrike(ServerLevel server, ServerPlayer player, ItemStack stack, int hits) {
		int rageLevel = getLevel(server, stack, RAGE);
		if (hits <= 0 || rageLevel <= 0) {
			return;
		}
		State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		double cap = capHearts(rageLevel) * 2;
		s.comboBonus = Math.min(cap, s.comboBonus + hits * perEnemyHearts(rageLevel) * 2);
		s.lastActiveTick = player.tickCount;
	}

	/** Runs every server tick. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = (ServerLevel) player.level();
			State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());

			// Lose the combo bonus after 5 seconds out of combat
			if (s.comboBonus > 0) {
				int lastCombat = Math.max(s.lastActiveTick,
						Math.max(player.getLastHurtMobTimestamp(), player.getLastHurtByMobTimestamp()));
				if (s.lastActiveTick > player.tickCount
						|| player.tickCount - lastCombat > COMBAT_TIMEOUT_TICKS) {
					s.comboBonus = 0;
				}
			}

			// How much bonus should apply right now
			double desired = 0;
			ItemStack stack = player.getMainHandItem();
			int rageLevel = stack.is(ItemTags.SWORDS) ? getLevel(level, stack, RAGE) : 0;
			if (rageLevel > 0) {
				if (getLevel(level, stack, LightningStrikeAbility.LIGHTNING_STRIKE) > 0) {
					desired = Math.min(s.comboBonus, capHearts(rageLevel) * 2);
				} else {
					desired = aloneHearts(rageLevel) * 2;
				}
			}

			AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (attr == null) {
				continue;
			}
			AttributeModifier current = attr.getModifier(MODIFIER_ID);
			double currentValue = current == null ? 0 : current.amount();
			if (currentValue != desired) {
				attr.removeModifier(MODIFIER_ID);
				if (desired > 0) {
					attr.addTransientModifier(new AttributeModifier(
							MODIFIER_ID, desired, AttributeModifier.Operation.ADD_VALUE));
				}
			}
		}
	}
}
