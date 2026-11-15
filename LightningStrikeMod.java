package com.example.lightningstrike;

import net.fabricmc.api.ModInitializer;

public class LightningStrikeMod implements ModInitializer {
	public static final String MOD_ID = "lightningstrike";

	@Override
	public void onInitialize() {
		LightningStrikeAbility.register();
	}
}
