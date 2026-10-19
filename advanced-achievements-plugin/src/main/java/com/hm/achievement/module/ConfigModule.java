package com.hm.achievement.module;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Named;
import javax.inject.Singleton;

import org.bukkit.configuration.file.YamlConfiguration;

import com.hm.achievement.category.Category;

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
	StringBuilder providePluginHeader() {
		return new StringBuilder();
	}

	@Provides
	@Singleton
	@Named("main")
	YamlConfiguration providesMainConfig() {
		return new YamlConfiguration();
	}

	@Provides
	@Singleton
	@Named("lang")
	YamlConfiguration providesLangConfig() {
		return new YamlConfiguration();
	}

	@Provides
	@Singleton
	@Named("gui")
	YamlConfiguration providesGuiConfig() {
		return new YamlConfiguration();
	}

}
