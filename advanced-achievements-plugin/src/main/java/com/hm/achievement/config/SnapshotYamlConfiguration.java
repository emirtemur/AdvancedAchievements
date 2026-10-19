package com.hm.achievement.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * YAML configuration whose content is replaced as a whole when loaded. Reloads therefore never expose an empty or
 * partially loaded configuration to readers on other threads (region threads with Folia), whereas a regular
 * YamlConfiguration clears and refills its sections in place.
 *
 * Every accessor of MemorySection that doesn't go through {@link #get(String, Object)} is delegated explicitly.
 *
 * @author LucidAPs
 */
public class SnapshotYamlConfiguration extends YamlConfiguration {

	private volatile YamlConfiguration snapshot = new YamlConfiguration();

	@Override
	public void loadFromString(String contents) throws InvalidConfigurationException {
		YamlConfiguration replacement = new YamlConfiguration();
		replacement.loadFromString(contents);
		snapshot = replacement;
	}

	@Override
	public String saveToString() {
		return snapshot.saveToString();
	}

	@Override
	public Object get(String path) {
		return snapshot.get(path);
	}

	@Override
	public Object get(String path, Object def) {
		return snapshot.get(path, def);
	}

	@Override
	public Set<String> getKeys(boolean deep) {
		return snapshot.getKeys(deep);
	}

	@Override
	public Map<String, Object> getValues(boolean deep) {
		return snapshot.getValues(deep);
	}

	@Override
	public boolean contains(String path) {
		return snapshot.contains(path);
	}

	@Override
	public boolean contains(String path, boolean ignoreDefault) {
		return snapshot.contains(path, ignoreDefault);
	}

	@Override
	public boolean isSet(String path) {
		return snapshot.isSet(path);
	}

	@Override
	public void set(String path, Object value) {
		snapshot.set(path, value);
	}

	@Override
	public ConfigurationSection createSection(String path) {
		return snapshot.createSection(path);
	}

	@Override
	public ConfigurationSection createSection(String path, Map<?, ?> map) {
		return snapshot.createSection(path, map);
	}

	@Override
	public ConfigurationSection getConfigurationSection(String path) {
		return snapshot.getConfigurationSection(path);
	}

	@Override
	public boolean isConfigurationSection(String path) {
		return snapshot.isConfigurationSection(path);
	}

	@Override
	public List<String> getComments(String path) {
		return snapshot.getComments(path);
	}

	@Override
	public List<String> getInlineComments(String path) {
		return snapshot.getInlineComments(path);
	}

	@Override
	public void setComments(String path, List<String> comments) {
		snapshot.setComments(path, comments);
	}

	@Override
	public void setInlineComments(String path, List<String> comments) {
		snapshot.setInlineComments(path, comments);
	}
}
