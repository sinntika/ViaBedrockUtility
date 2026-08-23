package org.oryxel.viabedrockutility.fabric;

import net.fabricmc.api.ModInitializer;

import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViaBedrockUtilityFabric implements ModInitializer {
	public static final String MOD_ID = "viabedrockutility";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Level!");
		ViaBedrockUtility.getInstance().init();
	}
}