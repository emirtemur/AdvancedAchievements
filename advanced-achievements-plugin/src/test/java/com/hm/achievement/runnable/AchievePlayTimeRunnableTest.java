package com.hm.achievement.runnable;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.scheduler.TaskScheduler;

class AchievePlayTimeRunnableTest {

	@Test
	void shouldProcessEachPlayerOnTheThreadOwningThem() {
		TaskScheduler taskScheduler = mock(TaskScheduler.class);
		CacheManager cacheManager = mock(CacheManager.class);
		Player first = player();
		Player second = player();

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class, RETURNS_DEEP_STUBS));
			bukkit.when(Bukkit::getOnlinePlayers).then(invocation -> List.of(first, second));
			AchievePlayTimeRunnable underTest = new AchievePlayTimeRunnable(new YamlConfiguration(),
					new AchievementMap(), cacheManager, taskScheduler);
			underTest.extractConfigurationParameters();

			underTest.run();
		}

		ArgumentCaptor<Runnable> firstUpdate = ArgumentCaptor.forClass(Runnable.class);
		ArgumentCaptor<Runnable> secondUpdate = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).runOnEntityThread(eq(first), firstUpdate.capture());
		verify(taskScheduler).runOnEntityThread(eq(second), secondUpdate.capture());
		verifyNoInteractions(cacheManager);

		UUID firstId = first.getUniqueId();
		UUID secondId = second.getUniqueId();
		firstUpdate.getValue().run();
		verify(cacheManager).getAndIncrementStatisticAmount(eq(NormalAchievements.PLAYEDTIME), eq(firstId), anyInt());
		verify(cacheManager, never()).getAndIncrementStatisticAmount(eq(NormalAchievements.PLAYEDTIME), eq(secondId),
				anyInt());
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
