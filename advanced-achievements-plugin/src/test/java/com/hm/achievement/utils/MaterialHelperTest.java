package com.hm.achievement.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaterialHelperTest {

	@Mock
	private PotionMeta potionMeta;

	@Mock
	private ItemStack itemStack;

	@Test
	void shouldReturnFalseForWaterPotion() {
		when(potionMeta.getBasePotionType()).thenReturn(PotionType.WATER);
		when(itemStack.getItemMeta()).thenReturn(potionMeta);
		when(itemStack.getType()).thenReturn(Material.POTION);
		MaterialHelper underTest = new MaterialHelper(null);

		assertFalse(underTest.isAnyPotionButWater(itemStack));
	}

	@Test
	void shouldReturnTrueForOtherPotion() {
		when(potionMeta.getBasePotionType()).thenReturn(PotionType.HARMING);
		when(itemStack.getItemMeta()).thenReturn(potionMeta);
		when(itemStack.getType()).thenReturn(Material.POTION);
		MaterialHelper underTest = new MaterialHelper(null);

		assertTrue(underTest.isAnyPotionButWater(itemStack));
	}

	@Test
	void shouldReturnFalseForOtherMaterial() {
		when(itemStack.getType()).thenReturn(Material.SPLASH_POTION);
		MaterialHelper underTest = new MaterialHelper(null);

		assertFalse(underTest.isAnyPotionButWater(itemStack));
	}

	@Test
	void shouldMatchModernMaterialNames() {
		MaterialHelper underTest = new MaterialHelper(mock(Logger.class));

		assertEquals(Material.DIRT_PATH, underTest.matchMaterial("dirt_path", Material.STONE, "gui.yml"));
	}

	@Test
	void shouldNotMatchLegacyMaterialNames() {
		// Legacy lookups make the server load its legacy material support, stalling it for several seconds.
		Logger logger = mock(Logger.class);
		MaterialHelper underTest = new MaterialHelper(logger);

		assertEquals(Material.STONE, underTest.matchMaterial("grass_path", Material.STONE, "gui.yml"));
		verify(logger).warning(contains("\"grass_path\" used in gui.yml is invalid"));
	}

	@Test
	void defaultGuiConfigurationShouldOnlyUseModernMaterialNames() {
		YamlConfiguration guiConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(
				MaterialHelperTest.class.getResourceAsStream("/gui.yml"), StandardCharsets.UTF_8));
		for (String path : guiConfig.getKeys(true)) {
			if (path.endsWith("Item") && guiConfig.isString(path)) {
				String name = guiConfig.getString(path);
				assertNotNull(Material.matchMaterial(name), path + " uses unknown or legacy material " + name);
			}
		}
	}
}
