package com.hm.achievement.command.executable;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.hm.achievement.category.CommandAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.domain.Achievement.AchievementBuilder;
import com.hm.achievement.listener.PlayerAdvancedAchievementListener;
import com.hm.achievement.scheduler.TaskScheduler;

class GiveCommandTest {

	@Test
	void shouldGiveConfiguredAchievementWithoutTargetPermission() {
		YamlConfiguration mainConfig = new YamlConfiguration();
		mainConfig.set("MultiCommand", false);
		YamlConfiguration langConfig = new YamlConfiguration();
		langConfig.set("achievement-already-received", "Already received");
		langConfig.set("achievement-given", "Achievement given!");
		langConfig.set("achievement-not-found", "Not found: CLOSEST_MATCH");
		AchievementMap achievementMap = new AchievementMap();
		Achievement achievement = new AchievementBuilder()
				.category(CommandAchievements.COMMANDS)
				.subcategory("daily")
				.name("command_daily")
				.displayName("Daily")
				.build();
		achievementMap.put(achievement);
		CacheManager cacheManager = mock(CacheManager.class);
		PlayerAdvancedAchievementListener achievementListener = mock(PlayerAdvancedAchievementListener.class);
		GiveCommand underTest = new GiveCommand(mainConfig, langConfig, new StringBuffer(), cacheManager, achievementMap,
				achievementListener, mock(TaskScheduler.class));
		underTest.extractConfigurationParameters();
		CommandSender sender = mock(CommandSender.class);
		Player player = mock(Player.class);
		when(player.hasPermission(anyString())).thenReturn(false);

		underTest.onExecuteForPlayer(sender, new String[] { "give", "daily", "Tealon" }, player);

		verify(achievementListener).awardAchievement(player, achievement);
		verify(sender).sendMessage("Achievement given!");
		verify(player, never()).hasPermission(anyString());
	}

	@Test
	void shouldStillRequireGivePermissionFromCommandSender() {
		YamlConfiguration langConfig = new YamlConfiguration();
		langConfig.set("no-permissions", "No permission");
		PlayerAdvancedAchievementListener achievementListener = mock(PlayerAdvancedAchievementListener.class);
		GiveCommand underTest = new GiveCommand(new YamlConfiguration(), langConfig, new StringBuffer(),
				mock(CacheManager.class), new AchievementMap(), achievementListener, mock(TaskScheduler.class));
		underTest.extractConfigurationParameters();
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission("achievement.give")).thenReturn(false);

		underTest.execute(sender, new String[] { "give", "daily", "Tealon" });

		verify(sender).hasPermission("achievement.give");
		verify(sender).sendMessage("No permission");
		verifyNoInteractions(achievementListener);
	}

	@Test
	void shouldGiveAchievementOnTargetPlayerThread() {
		YamlConfiguration mainConfig = new YamlConfiguration();
		mainConfig.set("MultiCommand", false);
		YamlConfiguration langConfig = new YamlConfiguration();
		langConfig.set("achievement-given", "Achievement given!");
		AchievementMap achievementMap = new AchievementMap();
		Achievement achievement = new AchievementBuilder()
				.category(CommandAchievements.COMMANDS)
				.subcategory("daily")
				.name("command_daily")
				.displayName("Daily")
				.build();
		achievementMap.put(achievement);
		PlayerAdvancedAchievementListener achievementListener = mock(PlayerAdvancedAchievementListener.class);
		TaskScheduler taskScheduler = mock(TaskScheduler.class);
		GiveCommand underTest = new GiveCommand(mainConfig, langConfig, new StringBuffer(), mock(CacheManager.class),
				achievementMap, achievementListener, taskScheduler);
		underTest.extractConfigurationParameters();
		CommandSender sender = mock(CommandSender.class);
		Player player = mock(Player.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer("Tealon")).thenReturn(player);
			underTest.onExecute(sender, new String[] { "give", "daily", "Tealon" });
		}

		// With Folia, the "already received" check and the award must both happen on the player's region thread.
		ArgumentCaptor<Runnable> give = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).runOnEntityThread(eq(player), give.capture());
		verifyNoInteractions(achievementListener);
		give.getValue().run();
		verify(achievementListener).awardAchievement(player, achievement);
		verify(sender).sendMessage("Achievement given!");
	}
}
