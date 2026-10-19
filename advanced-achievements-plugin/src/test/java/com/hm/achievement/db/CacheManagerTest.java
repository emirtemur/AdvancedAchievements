package com.hm.achievement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.scheduler.TaskScheduler;

class CacheManagerTest {

	@Test
	void shouldBulkLoadMultipleAchievementStatisticsOnce() {
		AbstractDatabaseManager databaseManager = mock(AbstractDatabaseManager.class);
		CacheManager underTest = new CacheManager(mock(TaskScheduler.class), databaseManager);
		UUID playerId = UUID.randomUUID();
		when(databaseManager.getMultipleAchievementAmounts(playerId, MultipleAchievements.CRAFTS))
				.thenReturn(Map.of("diamond_axe", 7L));

		Map<String, Long> firstResult = underTest.getMultipleAchievementAmounts(MultipleAchievements.CRAFTS,
				Arrays.asList("diamond_axe", "stick"), playerId);
		Map<String, Long> secondResult = underTest.getMultipleAchievementAmounts(MultipleAchievements.CRAFTS,
				Arrays.asList("diamond_axe", "stick"), playerId);

		assertEquals(Map.of("diamond_axe", 7L, "stick", 0L), firstResult);
		assertEquals(firstResult, secondResult);
		verify(databaseManager, times(1)).getMultipleAchievementAmounts(playerId, MultipleAchievements.CRAFTS);
	}

	@Test
	void shouldUseJobsRebornDefaultForMissingStatistics() {
		AbstractDatabaseManager databaseManager = mock(AbstractDatabaseManager.class);
		CacheManager underTest = new CacheManager(mock(TaskScheduler.class), databaseManager);
		UUID playerId = UUID.randomUUID();
		when(databaseManager.getMultipleAchievementAmounts(playerId, MultipleAchievements.JOBSREBORN))
				.thenReturn(Map.of());

		Map<String, Long> result = underTest.getMultipleAchievementAmounts(MultipleAchievements.JOBSREBORN,
				Arrays.asList("hunter"), playerId);

		assertEquals(Map.of("hunter", 1L), result);
	}

	@Test
	void concurrentIncrementsForSamePlayerShouldNotBeLost() throws Exception {
		AbstractDatabaseManager databaseManager = mock(AbstractDatabaseManager.class);
		CacheManager underTest = new CacheManager(mock(TaskScheduler.class), databaseManager);
		UUID playerId = UUID.randomUUID();
		when(databaseManager.getNormalAchievementAmount(playerId, NormalAchievements.DROPS)).thenReturn(100L);
		int threads = 8;
		int incrementsPerThread = 1_000;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					for (int j = 0; j < incrementsPerThread; j++) {
						underTest.getAndIncrementStatisticAmount(NormalAchievements.DROPS, playerId, 1);
					}
					return null;
				}));
			}
			start.countDown();
			for (Future<?> future : futures) {
				future.get(10, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}

		assertEquals(100L + threads * incrementsPerThread,
				underTest.getAndIncrementStatisticAmount(NormalAchievements.DROPS, playerId, 0));
	}

	@Test
	void staleCacheRemovalShouldBeDelegatedToGlobalThread() {
		TaskScheduler taskScheduler = mock(TaskScheduler.class);
		CacheManager underTest = new CacheManager(taskScheduler, mock(AbstractDatabaseManager.class));
		UUID playerId = UUID.randomUUID();
		CachedStatistic statistic = new CachedStatistic(3L, true);
		statistic.signalPlayerDisconnection();
		underTest.getHashMap(NormalAchievements.DROPS).put(playerId, statistic);

		underTest.cleanStaleCaches();

		ArgumentCaptor<Runnable> removal = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).runGlobal(removal.capture());
		assertTrue(underTest.getHashMap(NormalAchievements.DROPS).containsKey(playerId));
		removal.getValue().run();
		assertFalse(underTest.getHashMap(NormalAchievements.DROPS).containsKey(playerId));
	}

	@Test
	void staleCacheRemovalShouldKeepStatisticIncrementedInTheMeantime() {
		TaskScheduler taskScheduler = mock(TaskScheduler.class);
		CacheManager underTest = new CacheManager(taskScheduler, mock(AbstractDatabaseManager.class));
		UUID playerId = UUID.randomUUID();
		CachedStatistic statistic = new CachedStatistic(3L, true);
		statistic.signalPlayerDisconnection();
		underTest.getHashMap(NormalAchievements.DROPS).put(playerId, statistic);
		underTest.cleanStaleCaches();
		ArgumentCaptor<Runnable> removal = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).runGlobal(removal.capture());

		// Player reconnects and the statistic is increased before the removal runs on the global thread.
		assertEquals(4L, underTest.getAndIncrementStatisticAmount(NormalAchievements.DROPS, playerId, 1));
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(mock(Player.class));
			removal.getValue().run();
		}

		assertEquals(statistic, underTest.getHashMap(NormalAchievements.DROPS).get(playerId));
		assertFalse(statistic.isDatabaseConsistent());
		assertFalse(statistic.didPlayerDisconnect());
	}

	@Test
	void incrementAfterStaleCacheRemovalShouldReloadFromDatabase() {
		TaskScheduler taskScheduler = mock(TaskScheduler.class);
		AbstractDatabaseManager databaseManager = mock(AbstractDatabaseManager.class);
		CacheManager underTest = new CacheManager(taskScheduler, databaseManager);
		UUID playerId = UUID.randomUUID();
		CachedStatistic statistic = new CachedStatistic(3L, true);
		statistic.signalPlayerDisconnection();
		underTest.getHashMap(NormalAchievements.DROPS).put(playerId, statistic);
		when(databaseManager.getNormalAchievementAmount(playerId, NormalAchievements.DROPS)).thenReturn(3L);
		underTest.cleanStaleCaches();
		ArgumentCaptor<Runnable> removal = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).runGlobal(removal.capture());
		removal.getValue().run();

		assertEquals(4L, underTest.getAndIncrementStatisticAmount(NormalAchievements.DROPS, playerId, 1));
		verify(databaseManager).getNormalAchievementAmount(playerId, NormalAchievements.DROPS);
	}

	@Test
	void staleCacheCleaningShouldIgnoreConnectedPlayers() {
		TaskScheduler taskScheduler = mock(TaskScheduler.class);
		CacheManager underTest = new CacheManager(taskScheduler, mock(AbstractDatabaseManager.class));
		underTest.getHashMap(NormalAchievements.DROPS).put(UUID.randomUUID(), new CachedStatistic(3L, true));

		underTest.cleanStaleCaches();

		verify(taskScheduler, never()).runGlobal(any());
	}
}
