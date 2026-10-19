package com.hm.achievement.runnable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.scheduler.TaskScheduler;

class AchieveDistanceRunnableTest {

	@Test
	void shouldAcceptMissingTeleportDestination() {
		// EntityTeleportEvent.getTo() is nullable, and player locations are stored in a concurrent map.
		AchieveDistanceRunnable underTest = new AchieveDistanceRunnable(new YamlConfiguration(), new AchievementMap(),
				mock(CacheManager.class), new HashSet<>(), mock(TaskScheduler.class));
		UUID playerId = UUID.randomUUID();
		underTest.updateLocation(playerId, new Location(mock(World.class), 1, 2, 3));

		assertDoesNotThrow(() -> underTest.updateLocation(playerId, null));
		assertDoesNotThrow(() -> underTest.updateLocation(playerId, new Location(mock(World.class), 4, 5, 6)));
	}
}
