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
 * Rage (levels I to VII):
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

	// Index 0 = level I ... index 6 = level VII.
	/** Bonus with Rage alone, in hearts: I = 0.40 ... V = 0.60, VII = 0.70 */
	private static final double[] ALONE = {0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70};
	/** Bonus per enemy hit by Lightning Strike, in hearts: I = 0.40 ... V = 0.52, VII = 0.56 */
	private static final double[] PER_ENEMY = {0.40, 0.43, 0.46, 0.49, 0.52, 0.54, 0.56};
	/** Maximum combo bonus, in hearts: I = 2.2 ... V = 2.9, VII = 3.1 */
	private static final double[] CAP = {2.2, 2.4, 2.6, 2.8, 2.9, 3.0, 3.1};

	private static double pick(double[] table, int level) {
		return table[Math.max(1, Math.min(table.length, level)) - 1];
	}

	private static double aloneHearts(int level) {
		return pick(ALONE, level);
	}

	private static double perEnemyHearts(int level) {
		return pick(PER_ENEMY, level);
	}

	private static double capHearts(int level) {
		return pick(CAP, level);
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
