package com.hm.achievement.command.executable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hm.achievement.db.AbstractDatabaseManager;
import com.hm.achievement.scheduler.TaskScheduler;
import com.hm.achievement.utils.SoundPlayer;

class TopCommandTest {

	private static final String HEADER = "[AACH] ";
	private static final String TOP_MESSAGE = HEADER + "Top achievers:";

	private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
	private final AbstractDatabaseManager databaseManager = mock(AbstractDatabaseManager.class);
	private TopCommand underTest;

	@BeforeEach
	void setUp() {
		YamlConfiguration mainConfig = new YamlConfiguration();
		mainConfig.set("Color", "5");
		mainConfig.set("TopList", 5);
		mainConfig.set("AdditionalEffects", false);
		mainConfig.set("Sound", false);
		mainConfig.set("SoundRanking", "entity_firework_rocket_blast");
		YamlConfiguration langConfig = new YamlConfiguration();
		langConfig.set("top-achievement", "Top achievers:");
		langConfig.set("player-rank", "Rank:");
		langConfig.set("not-ranked", "Not ranked.");
		underTest = new TopCommand(mainConfig, langConfig, new StringBuilder(HEADER), taskScheduler,
				Logger.getAnonymousLogger(), databaseManager, mock(SoundPlayer.class));
		underTest.extractConfigurationParameters();
	}

	@Test
	void shouldQueryDatabaseOffThreadAndAnswerEachSenderOnItsOwnThread() {
		when(databaseManager.getTopList(0L)).thenReturn(new LinkedHashMap<>());
		CommandSender first = mock(CommandSender.class);
		CommandSender second = mock(CommandSender.class);

		underTest.onExecute(first, new String[] { "top" });
		underTest.onExecute(second, new String[] { "top" });

		// A single refresh is triggered while one is already in progress.
		Runnable refresh = captureAsyncRefresh();
		verify(databaseManager, never()).getTopList(0L);
		refresh.run();
		verify(databaseManager).getTopList(0L);

		ArgumentCaptor<Runnable> responses = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).runForSender(eq(first), responses.capture());
		verify(taskScheduler).runForSender(eq(second), responses.capture());
		verify(first, never()).sendMessage(TOP_MESSAGE);
		responses.getAllValues().forEach(Runnable::run);
		verify(first).sendMessage(TOP_MESSAGE);
		verify(second).sendMessage(TOP_MESSAGE);
	}

	@Test
	void shouldServeFreshRankingsFromCacheWithoutScheduling() {
		when(databaseManager.getTopList(0L)).thenReturn(new LinkedHashMap<>());
		underTest.onExecute(mock(CommandSender.class), new String[] { "top" });
		captureAsyncRefresh().run();
		CommandSender sender = mock(CommandSender.class);

		underTest.onExecute(sender, new String[] { "top" });

		verify(sender).sendMessage(TOP_MESSAGE);
		verify(taskScheduler, times(1)).runAsync(any());
		verify(taskScheduler, never()).runForSender(eq(sender), any());
	}

	@Test
	void shouldAllowNewRefreshAfterDatabaseFailure() {
		when(databaseManager.getTopList(0L)).thenThrow(new IllegalStateException("database down"))
				.thenReturn(new LinkedHashMap<>());
		CommandSender sender = mock(CommandSender.class);

		underTest.onExecute(sender, new String[] { "top" });
		captureAsyncRefresh().run();
		underTest.onExecute(sender, new String[] { "top" });

		verify(taskScheduler, times(2)).runAsync(any());
		verify(taskScheduler, never()).runForSender(any(), any());
	}

	@Test
	void concurrentExecutionsShouldTriggerSingleRefresh() throws Exception {
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<CommandSender> senders = new ArrayList<>();
		List<Future<?>> futures = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				CommandSender sender = mock(CommandSender.class);
				senders.add(sender);
				futures.add(executor.submit(() -> {
					start.await();
					underTest.onExecute(sender, new String[] { "top" });
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
		when(databaseManager.getTopList(0L)).thenReturn(new LinkedHashMap<>());

		captureAsyncRefresh().run();

		for (CommandSender sender : senders) {
			verify(taskScheduler).runForSender(eq(sender), any());
		}
	}

	private Runnable captureAsyncRefresh() {
		ArgumentCaptor<Runnable> refresh = ArgumentCaptor.forClass(Runnable.class);
		verify(taskScheduler).runAsync(refresh.capture());
		return refresh.getValue();
	}
}
