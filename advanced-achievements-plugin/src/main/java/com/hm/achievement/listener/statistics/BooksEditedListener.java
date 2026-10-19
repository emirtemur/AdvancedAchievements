package com.hm.achievement.listener.statistics;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerEditBookEvent;

import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.scheduler.TaskScheduler;

/**
 * Listener class to deal with BooksEdited achievements.
 *
 * @author Pyves
 *
 */
@Singleton
public class BooksEditedListener extends AbstractRateLimitedListener {

	@Inject
	public BooksEditedListener(@Named("main") YamlConfiguration mainConfig, AchievementMap achievementMap,
			CacheManager cacheManager, TaskScheduler taskScheduler,
			@Named("lang") YamlConfiguration langConfig) {
		super(NormalAchievements.BOOKSEDITED, mainConfig, achievementMap, cacheManager, taskScheduler, langConfig);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerEditBookEvent(PlayerEditBookEvent event) {
		updateStatisticAndAwardAchievementsIfAvailable(event.getPlayer(), 1);
	}

}
