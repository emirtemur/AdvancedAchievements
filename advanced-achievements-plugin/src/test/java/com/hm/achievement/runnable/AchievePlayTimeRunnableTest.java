package com.hm.achievement.runnable;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;

class AchievePlayTimeRunnableTest {

	@Test
	void playerTaskShouldOnlyUpdateItsPlayerWithElapsedTime() throws Exception {
		CacheManager cacheManager = mock(CacheManager.class);
		AchievePlayTimeRunnable underTest = createRunnable(cacheManager);
		Player first = player();
		Player second = player();
		UUID firstId = first.getUniqueId();
		UUID secondId = second.getUniqueId();

		Runnable firstTask = underTest.createPlayerTask(first);
		underTest.createPlayerTask(second);
		verifyNoInteractions(cacheManager);
		Thread.sleep(5L);
		firstTask.run();

		verify(cacheManager).getAndIncrementStatisticAmount(eq(NormalAchievements.PLAYEDTIME), eq(firstId),
				intThat(millis -> millis >= 5));
		verify(cacheManager, never()).getAndIncrementStatisticAmount(eq(NormalAchievements.PLAYEDTIME), eq(secondId),
				anyInt());
	}

	@Test
	void playerTasksShouldTrackTimeIndependently() {
		CacheManager cacheManager = mock(CacheManager.class);
		AchievePlayTimeRunnable underTest = createRunnable(cacheManager);
		Player player = player();
		UUID playerId = player.getUniqueId();
		Runnable task = underTest.createPlayerTask(player);

		task.run();
		task.run();

		// Each run only accounts for the time elapsed since the previous run of the same task.
		verify(cacheManager, times(2)).getAndIncrementStatisticAmount(
				eq(NormalAchievements.PLAYEDTIME), eq(playerId), intThat(millis -> millis < 1000));
	}

	private AchievePlayTimeRunnable createRunnable(CacheManager cacheManager) {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class, RETURNS_DEEP_STUBS));
			AchievePlayTimeRunnable runnable = new AchievePlayTimeRunnable(new YamlConfiguration(),
					new AchievementMap(), cacheManager);
			runnable.extractConfigurationParameters();
			return runnable;
		}
	}

	private Player player() {
		Player player = mock(Player.class);
		World world = mock(World.class);
		UUID uuid = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		doReturn(world).when(player).getWorld();
		when(world.getName()).thenReturn("world");
		return player;
	}
}
