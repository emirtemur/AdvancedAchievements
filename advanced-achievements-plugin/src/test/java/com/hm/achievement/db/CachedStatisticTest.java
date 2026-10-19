package com.hm.achievement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class CachedStatisticTest {

	@Test
	void marksExactWrittenRevisionAsPersisted() {
		CachedStatistic statistic = new CachedStatistic(4L, true);
		assertNull(statistic.snapshotForWrite());

		statistic.setValue(5L);
		CachedStatistic.WriteSnapshot snapshot = statistic.snapshotForWrite();
		assertNotNull(snapshot);
		assertEquals(5L, snapshot.getValue());
		assertFalse(statistic.isDatabaseConsistent());

		statistic.markPersisted(snapshot.getRevision());
		assertTrue(statistic.isDatabaseConsistent());
	}

	@Test
	void newerUpdateRemainsDirtyWhenOlderSnapshotCompletes() {
		CachedStatistic statistic = new CachedStatistic(1L, true);
		statistic.setValue(2L);
		CachedStatistic.WriteSnapshot firstWrite = statistic.snapshotForWrite();

		statistic.setValue(3L);
		statistic.markPersisted(firstWrite.getRevision());

		assertFalse(statistic.isDatabaseConsistent());
		assertEquals(3L, statistic.snapshotForWrite().getValue());
	}

	@Test
	void incrementShouldReturnUpdatedValueAndMarkStatisticDirty() {
		CachedStatistic statistic = new CachedStatistic(10L, true);

		assertEquals(15L, statistic.increment(5L));

		assertEquals(15L, statistic.getValue());
		assertFalse(statistic.isDatabaseConsistent());
	}

	@Test
	void concurrentIncrementsShouldNotBeLost() throws Exception {
		// With Folia, the same statistic may be updated by several region threads.
		CachedStatistic statistic = new CachedStatistic(0L, true);
		int threads = 8;
		int incrementsPerThread = 10_000;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					for (int j = 0; j < incrementsPerThread; j++) {
						statistic.increment(1L);
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

		assertEquals((long) threads * incrementsPerThread, statistic.getValue());
	}
}
