package com.megatrex4;

import com.megatrex4.api.v1.InventoryWeightEvents;
import dev.ftb.mods.ftbranks.api.FTBRanksAPI;
import dev.ftb.mods.ftbranks.api.PermissionValue;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;

/**
 * Connects FTB Ranks with MT Inventory Weight.
 *
 * <p>It reads three permission nodes from the player's active FTB Ranks rank(s) and uses
 * them to modify the player's maximum inventory weight:
 *
 * <ul>
 *     <li>{@code inventoryweight.maxweight.add}        &ndash; flat bonus added to max weight</li>
 *     <li>{@code inventoryweight.maxweight.percent}    &ndash; percentage bonus (e.g. {@code 25} = +25%)</li>
 *     <li>{@code inventoryweight.maxweight.multiplier} &ndash; final multiplier (e.g. {@code 1.5} = x1.5)</li>
 * </ul>
 *
 * <p>Formula (matches the LuckPerms connector):
 * <pre>
 * result = (currentMaxWeight + add) * (1 + percent / 100) * multiplier
 * </pre>
 *
 * <p>FTB Ranks permission values can be numeric, so each node is read via
 * {@link PermissionValue}. A missing node falls back to a neutral default.
 *
 * <p>Example FTB Ranks config (ranks.snbt):
 * <pre>
 * vip {
 *     permissions: {
 *         "inventoryweight.maxweight.add": 50
 *         "inventoryweight.maxweight.percent": 25
 *         "inventoryweight.maxweight.multiplier": 1.5
 *     }
 * }
 * </pre>
 */
public class FTBRanksInventoryWeight implements ModInitializer {

	public static final String MOD_ID = "ftb-ranks-inventoryweight";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String NODE_ADD = "inventoryweight.maxweight.add";
	private static final String NODE_PERCENT = "inventoryweight.maxweight.percent";
	private static final String NODE_MULTIPLIER = "inventoryweight.maxweight.multiplier";

	@Override
	public void onInitialize() {
		InventoryWeightEvents.MODIFY_MAX_WEIGHT.register(FTBRanksInventoryWeight::modifyMaxWeight);

		LOGGER.info(
				"FTB Ranks Inventory Weight initialized. FTB Ranks loaded: {}",
				FabricLoader.getInstance().isModLoaded("ftbranks")
		);
	}

	private static float modifyMaxWeight(ServerPlayerEntity player, float currentMaxWeight) {
		if (!isFtbRanksReady()) {
			return currentMaxWeight;
		}

		float additive = readNode(player, NODE_ADD, 0.0f);
		float percent = readNode(player, NODE_PERCENT, 0.0f);
		float multiplier = readNode(player, NODE_MULTIPLIER, 1.0f);

		additive = Math.max(0.0f, additive);
		percent = Math.max(0.0f, percent);
		multiplier = Math.max(0.0f, multiplier);

		float percentMultiplier = 1.0f + percent / 100.0f;
		float result = (currentMaxWeight + additive) * percentMultiplier * multiplier;

		return Math.max(1.0f, result);
	}

	/**
	 * FTB Ranks is server-side; the API instance only exists once the server has started
	 * and {@code FTBRanksAPI.setup(...)} has run. Guard against early / missing access.
	 */
	private static boolean isFtbRanksReady() {
		if (!FabricLoader.getInstance().isModLoaded("ftbranks")) {
			return false;
		}

		try {
			return FTBRanksAPI.getInstance() != null;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * Reads a single FTB Ranks permission node as a float.
	 *
	 * <p>{@code ServerPlayerEntity} (Yarn) and {@code ServerPlayer} (the mapping FTB Ranks'
	 * API jar uses) are the same runtime class, so the player reference is passed through
	 * directly.
	 */
	private static float readNode(ServerPlayerEntity player, String node, float defaultValue) {
		PermissionValue value = FTBRanksAPI.getPermissionValue(player, node);

		if (value == null || value.isEmpty()) {
			return defaultValue;
		}

		// Prefer the precise double accessor when the node is numeric.
		OptionalDouble asDouble = value.asDouble();
		if (asDouble.isPresent()) {
			return sanitize((float) asDouble.getAsDouble(), defaultValue);
		}

		// Fall back to parsing the string form (covers string-typed numeric nodes).
		Float parsed = value.asString().map(FTBRanksInventoryWeight::parseFloat).orElse(null);
		return parsed == null ? defaultValue : sanitize(parsed, defaultValue);
	}

	private static float sanitize(float value, float defaultValue) {
		return Float.isFinite(value) ? value : defaultValue;
	}

	private static Float parseFloat(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return null;
		}

		try {
			float value = Float.parseFloat(rawValue.trim());
			return Float.isFinite(value) ? value : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
