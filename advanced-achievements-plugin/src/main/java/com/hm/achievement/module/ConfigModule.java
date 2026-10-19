package com.hm.achievement.module;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Named;
import javax.inject.Singleton;

import org.bukkit.configuration.file.YamlConfiguration;

import com.hm.achievement.category.Category;
import com.hm.achievement.config.SnapshotYamlConfiguration;

import dagger.Module;
import dagger.Provides;

@Module
public class ConfigModule {

	@Provides
	@Singleton
	Set<Category> provideDisabledCategories() {
		// Concurrent set as it is read by several region threads with Folia while it may be refilled by a reload.
		return ConcurrentHashMap.newKeySet();
	}

	@Provides
	@Singleton
	StringBuffer providePluginHeader() {
		// Synchronised buffer, as the header is read by several region threads with Folia while a reload replaces it.
		return new StringBuffer();
	}

	// Configurations are replaced as a whole on reload, as they are read by several region threads with Folia.
	@Provides
	@Singleton
	@Named("main")
	YamlConfiguration providesMainConfig() {
		return new SnapshotYamlConfiguration();
	}

	@Provides
	@Singleton
	@Named("lang")
	YamlConfiguration providesLangConfig() {
		return new SnapshotYamlConfiguration();
	}

	@Provides
	@Singleton
	@Named("gui")
	YamlConfiguration providesGuiConfig() {
		return new SnapshotYamlConfiguration();
	}

}
