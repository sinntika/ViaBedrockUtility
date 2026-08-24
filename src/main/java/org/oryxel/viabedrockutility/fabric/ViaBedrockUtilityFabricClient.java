package org.oryxel.viabedrockutility.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.oryxel.viabedrockutility.block.model.CustomBlockModelPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client side of the mod's start up.
 *
 * <p>Model loading is a client concern, so it is registered here rather than in
 * the main entrypoint, which also runs on a server where these classes do not
 * exist.
 */
public final class ViaBedrockUtilityFabricClient implements ClientModInitializer {

	private static final Logger LOGGER =
			LoggerFactory.getLogger("ViaBedrockUtility");

	@Override
	public void onInitializeClient() {
		try {
			CustomBlockModelPlugin.register();
		} catch (final Throwable throwable) {
			// Losing the custom block models must not stop the rest of the mod.
			LOGGER.error("Could not set up the custom block models", throwable);
		}
	}
}
