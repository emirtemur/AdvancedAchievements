package com.hm.achievement.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SnapshotYamlConfigurationTest {

	private static final String CONTENT = "Color: '5'\nTopList: 8\nSound: true\nExcludedWorlds:\n- nether\n"
			+ "Section:\n  Child: value\n  Other: 3\n";

	@Test
	void shouldBehaveLikeRegularYamlConfiguration() throws Exception {
		SnapshotYamlConfiguration underTest = new SnapshotYamlConfiguration();
		underTest.loadFromString(CONTENT);

		assertEquals("5", underTest.getString("Color"));
		assertEquals(8, underTest.getInt("TopList"));
		assertTrue(underTest.getBoolean("Sound"));
		assertEquals(List.of("nether"), underTest.getStringList("ExcludedWorlds"));
		assertTrue(underTest.isList("ExcludedWorlds"));
		assertTrue(underTest.isString("Section.Child"));
		assertTrue(underTest.contains("Section.Other"));
		assertTrue(underTest.isConfigurationSection("Section"));
		ConfigurationSection section = underTest.getConfigurationSection("Section");
		assertNotNull(section);
		assertEquals(Set.of("Child", "Other"), section.getKeys(false));
		assertEquals(List.of("Color", "TopList", "Sound", "ExcludedWorlds", "Section"),
				List.copyOf(underTest.getKeys(false)));
		assertNull(underTest.getString("Missing"));
		assertEquals("fallback", underTest.getString("Missing", "fallback"));
	}

	@Test
	void shouldRoundTripThroughRegularConfiguration() throws Exception {
		SnapshotYamlConfiguration underTest = new SnapshotYamlConfiguration();
		underTest.loadFromString(CONTENT);
		YamlConfiguration copy = new YamlConfiguration();
		copy.loadFromString(underTest.saveToString());

		assertEquals(underTest.saveToString(), copy.saveToString());
		assertEquals(underTest.getKeys(true), copy.getKeys(true));
	}

	@Test
	void shouldReplaceContentAsAWhole() throws Exception {
		SnapshotYamlConfiguration underTest = new SnapshotYamlConfiguration();
		underTest.loadFromString(CONTENT);

		underTest.loadFromString("Color: '6'\n");

		assertEquals("6", underTest.getString("Color"));
		assertFalse(underTest.contains("TopList"));
	}

	@Test
	void shouldSupportSettingValues() {
		SnapshotYamlConfiguration underTest = new SnapshotYamlConfiguration();

		underTest.set("Section.Child", "value");

		assertEquals("value", underTest.getString("Section.Child"));
		assertTrue(underTest.isSet("Section.Child"));
	}

	@Test
	void readersShouldNeverObserveAPartiallyLoadedConfiguration() throws Exception {
		// With Folia, region threads read the configuration while /aach reload replaces it on the global thread.
		SnapshotYamlConfiguration underTest = new SnapshotYamlConfiguration();
		underTest.loadFromString(CONTENT);
		AtomicBoolean reloading = new AtomicBoolean(true);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> reader = executor.submit(() -> {
				while (reloading.get()) {
					assertEquals("5", underTest.getString("Color"));
					assertEquals(List.of("nether"), underTest.getStringList("ExcludedWorlds"));
				}
				return null;
			});
			for (int i = 0; i < 500; i++) {
				underTest.loadFromString(CONTENT);
			}
			reloading.set(false);
			reader.get(10, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}
	}
}
