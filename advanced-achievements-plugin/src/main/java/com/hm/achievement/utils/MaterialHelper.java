package com.hm.achievement.utils;

import java.util.Optional;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Class providing a few logging helper methods to retrieve Material instances via their names.
 *
 * @author Pyves
 */
@Singleton
public class MaterialHelper {

	private final Logger logger;

	@Inject
	public MaterialHelper(Logger logger) {
		this.logger = logger;
	}

	/**
	 * Tries to match a name to a Material instance.
	 *
	 * @param name the string to match
	 * @param usageLocation used for logging
	 * @return a Material wrapped inside an Optional or an empty Optional.
	 */
	public Optional<Material> matchMaterial(String name, String usageLocation) {
		return Optional.ofNullable(matchMaterial(name, null, usageLocation));
	}

	/**
	 * Tries to match a name to a Material instance and returns a default value if not found.
	 *
	 * @param name the string to match
	 * @param defaultMaterial returned if name could not be matched
	 * @param usageLocation used for logging
	 * @return a Material or null.
	 */
	public Material matchMaterial(String name, Material defaultMaterial, String usageLocation) {
		if (name == null || name.isEmpty()) {
			logger.warning(usageLocation + " is missing. Please check your configuration.");
			return defaultMaterial;
		}
		// Legacy (pre-1.13) names are deliberately not matched: looking them up makes the server load its legacy material
		// support, which stalls it for several seconds.
		Material material = Material.matchMaterial(name);
		if (material == null) {
			logger.warning("Material \"" + name + "\" used in " + usageLocation + " is invalid. Have you spelt the name "
					+ "correctly, is it available for your Minecraft version and is it a modern (1.13+) material name?");
			material = defaultMaterial;
		}
		return material;
	}

	/**
	 * Determines whether an item is any Material.POTION apart from water.
	 *
	 * @param itemStack
	 * @return true if the item is a non-water potion, false otherwise
	 */
	public boolean isAnyPotionButWater(ItemStack itemStack) {
		if (itemStack.getType() != Material.POTION) {
			return false;
		}
		PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
		if (meta == null) {
			return false;
		}
		if (meta.hasCustomEffects()) {
			// The potion has custom effects, so it's not water
			return true;
		}
		// Use Paper's extended method to get the base potion type
		PotionType type = meta.getBasePotionType();
		if (type == null) {
			// If base potion type is null and there are no custom effects, consider it as water
			return false;
		}
		return type != PotionType.WATER;
	}
}
