package com.example.lightningstrike;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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

	/** Tells the player in chat whenever their Rage bonus changes (hearts of extra damage). */
	private static void announce(ServerPlayer player, int rageLevel, boolean comboMode, double desired) {
		String text;
		if (desired > 0 && comboMode) {
			text = String.format(Locale.ROOT, "Rage combo: +%.2f hearts damage (max +%.1f)",
					desired / 2, capHearts(rageLevel));
		} else if (desired > 0) {
			text = String.format(Locale.ROOT, "Rage: +%.1f hearts damage", desired / 2);
		} else if (rageLevel > 0 && comboMode) {
			text = "Rage combo reset";
		} else {
			text = "Rage bonus gone";
		}
		// Sent only to this player, so only the holder sees it
		player.sendSystemMessage(rainbow(text, player.tickCount));
	}

	/** Rainbow text: each letter gets the next colour of the rainbow. The starting colour shifts over time. */
	private static Component rainbow(String text, int tick) {
		MutableComponent out = Component.empty();
		double shift = (tick % 60) / 60.0;
		int n = Math.max(1, text.length());
		for (int i = 0; i < text.length(); i++) {
			double hue = (shift + (double) i / n) % 1.0;
			out.append(Component.literal(String.valueOf(text.charAt(i)))
					.withStyle(Style.EMPTY.withColor(rainbowColor(hue))));
		}
		return out;
	}

	/** Full-brightness colour for a hue from 0 to 1, as 0xFFRRGGBB. */
	private static int rainbowColor(double hue) {
		double h = hue * 6;
		int sector = (int) Math.floor(h) % 6;
		int rise = (int) Math.round(255 * (h - Math.floor(h)));
		int fall = 255 - rise;
		int r, g, b;
		switch (sector) {
			case 0 -> { r = 255; g = rise; b = 0; }
			case 1 -> { r = fall; g = 255; b = 0; }
			case 2 -> { r = 0; g = 255; b = rise; }
			case 3 -> { r = 0; g = fall; b = 255; }
			case 4 -> { r = rise; g = 0; b = 255; }
			default -> { r = 255; g = 0; b = fall; }
		}
		return 0xFF000000 | (r << 16) | (g << 8) | b;
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
			boolean comboMode = false;
			ItemStack stack = player.getMainHandItem();
			int rageLevel = stack.is(ItemTags.SWORDS) ? getLevel(level, stack, RAGE) : 0;
			if (rageLevel > 0) {
				if (getLevel(level, stack, LightningStrikeAbility.LIGHTNING_STRIKE) > 0) {
					comboMode = true;
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
				announce(player, rageLevel, comboMode, desired);
			}
		}
	}
}
