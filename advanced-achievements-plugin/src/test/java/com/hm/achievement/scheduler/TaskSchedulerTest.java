package com.hm.achievement.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

class TaskSchedulerTest {

	private final Plugin plugin = mock(Plugin.class);
	private final Runnable task = mock(Runnable.class);
	private MockedStatic<Bukkit> bukkit;

	@BeforeEach
	void setUp() {
		bukkit = mockStatic(Bukkit.class);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	void shouldNotDetectFoliaOnRegularServers() {
		assertFalse(TaskScheduler.isFoliaServer());
	}

	@Nested
	class WithoutFolia {

		private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
		private final TaskScheduler underTest = new TaskScheduler(plugin, false);

		@BeforeEach
		void setUp() {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		}

		@Test
		void shouldUseBukkitSchedulerForGlobalAndAsyncTasks() {
			underTest.runGlobal(task);
			underTest.runGlobalLater(task, 5L);
			underTest.runGlobalTimer(task, 0L, 20L);
			underTest.runAsync(task);
			underTest.runAsyncTimer(task, 40L, 40L);

			verify(scheduler).runTask(plugin, task);
			verify(scheduler).runTaskLater(plugin, task, 5L);
			verify(scheduler).runTaskTimer(plugin, task, 0L, 20L);
			verify(scheduler).runTaskAsynchronously(plugin, task);
			verify(scheduler).runTaskTimerAsynchronously(plugin, task, 40L, 40L);
			bukkit.verify(Bukkit::getGlobalRegionScheduler, never());
			bukkit.verify(Bukkit::getAsyncScheduler, never());
		}

		@Test
		void shouldUseBukkitSchedulerForEntityAndSenderTasks() {
			Player player = mock(Player.class);
			ConsoleCommandSender console = mock(ConsoleCommandSender.class);

			underTest.runForEntity(player, task);
			underTest.runForEntityLater(player, task, 110L);
			underTest.runForSender(console, task);

			verify(scheduler, times(2)).runTask(plugin, task);
			verify(scheduler).runTaskLater(plugin, task, 110L);
			verify(player, never()).getScheduler();
		}

		@Test
		void shouldRunThreadBoundTasksImmediatelyOnMainThread() {
			Player player = mock(Player.class);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

			underTest.runOnEntityThread(player, task);
			underTest.runOnGlobalThread(task);

			verify(task, times(2)).run();
			verifyNoInteractions(scheduler);
			bukkit.verify(() -> Bukkit.isOwnedByCurrentRegion(any(Player.class)), never());
		}

		@Test
		void shouldScheduleThreadBoundTasksOnMainThreadWhenCalledFromAnotherThread() {
			Player player = mock(Player.class);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

			underTest.runOnEntityThread(player, task);
			underTest.runOnGlobalThread(task);

			verify(task, never()).run();
			verify(scheduler, times(2)).runTask(plugin, task);
		}

		@Test
		void shouldCancelUnderlyingBukkitTask() {
			BukkitTask bukkitTask = mock(BukkitTask.class);
			when(scheduler.runTaskTimer(plugin, task, 1L, 1L)).thenReturn(bukkitTask);

			underTest.runGlobalTimer(task, 1L, 1L).cancel();

			verify(bukkitTask).cancel();
		}

		@Test
		void shouldTolerateMissingBukkitTaskOnCancel() {
			assertDoesNotThrow(() -> underTest.runGlobalTimer(task, 1L, 1L).cancel());
		}
	}

	@Nested
	class WithFolia {

		private final GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
		private final AsyncScheduler asyncScheduler = mock(AsyncScheduler.class);
		private final EntityScheduler entityScheduler = mock(EntityScheduler.class);
		private final Player player = mock(Player.class);
		private final TaskScheduler underTest = new TaskScheduler(plugin, true);

		@BeforeEach
		void setUp() {
			bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(globalScheduler);
			bukkit.when(Bukkit::getAsyncScheduler).thenReturn(asyncScheduler);
			when(player.getScheduler()).thenReturn(entityScheduler);
		}

		@Test
		void shouldUseFoliaFlag() {
			assertTrue(underTest.isFolia());
		}

		@Test
		void shouldRunGlobalTasksOnGlobalRegion() {
			underTest.runGlobal(task);
			underTest.runOnGlobalThread(task);

			verify(globalScheduler, times(2)).execute(plugin, task);
			verify(task, never()).run();
			bukkit.verify(Bukkit::getScheduler, never());
		}

		@Test
		void shouldClampGlobalDelaysToOneTick() {
			underTest.runGlobalLater(task, 0L);
			underTest.runGlobalTimer(task, 0L, 0L);

			verify(globalScheduler).runDelayed(eq(plugin), any(), eq(1L));
			verify(globalScheduler).runAtFixedRate(eq(plugin), any(), eq(1L), eq(1L));
		}

		@Test
		void shouldConvertAsyncTimerTicksToMilliseconds() {
			underTest.runAsyncTimer(task, 40L, 1200L);

			verify(asyncScheduler).runAtFixedRate(eq(plugin), any(), eq(2000L), eq(60000L), eq(TimeUnit.MILLISECONDS));
		}

		@Test
		void shouldRunAsyncTasksOnAsyncScheduler() {
			underTest.runAsync(task);

			Consumer<ScheduledTask> scheduled = captureAsyncRunNow();
			verify(task, never()).run();
			scheduled.accept(mock(ScheduledTask.class));
			verify(task).run();
		}

		@Test
		void shouldRunEntityTasksOnEntityScheduler() {
			underTest.runForEntity(player, task);

			Consumer<ScheduledTask> scheduled = captureEntityRun();
			verify(task, never()).run();
			scheduled.accept(mock(ScheduledTask.class));
			verify(task).run();
		}

		@Test
		void shouldClampEntityDelaysToOneTick() {
			underTest.runForEntityLater(player, task, 0L);

			verify(entityScheduler).runDelayed(eq(plugin), any(), isNull(), eq(1L));
		}

		@Test
		void shouldRunImmediatelyWhenCurrentRegionOwnsEntity() {
			bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(player)).thenReturn(true);

			underTest.runOnEntityThread(player, task);

			verify(task).run();
			verifyNoInteractions(entityScheduler);
		}

		@Test
		void shouldHandOverToEntitySchedulerWhenAnotherRegionOwnsEntity() {
			bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(player)).thenReturn(false);

			underTest.runOnEntityThread(player, task);

			verify(task, never()).run();
			captureEntityRun().accept(mock(ScheduledTask.class));
			verify(task).run();
		}

		@Test
		void shouldRouteSendersToTheirOwningThread() {
			ConsoleCommandSender console = mock(ConsoleCommandSender.class);

			underTest.runForSender(player, task);
			underTest.runForSender(console, task);

			verify(entityScheduler).run(eq(plugin), any(), isNull());
			verify(globalScheduler).execute(plugin, task);
		}

		@Test
		void shouldCancelUnderlyingScheduledTask() {
			ScheduledTask scheduledTask = mock(ScheduledTask.class);
			when(globalScheduler.runAtFixedRate(eq(plugin), any(), anyLong(), anyLong())).thenReturn(scheduledTask);

			underTest.runGlobalTimer(task, 1L, 1L).cancel();

			verify(scheduledTask).cancel();
		}

		@Test
		void shouldIgnoreTasksForRemovedEntities() {
			// Entity schedulers return null once the entity has been removed, e.g. after a player disconnects.
			when(entityScheduler.run(eq(plugin), any(), isNull())).thenReturn(null);

			assertDoesNotThrow(() -> underTest.runForEntity(player, task));
		}

		@SuppressWarnings("unchecked")
		private Consumer<ScheduledTask> captureEntityRun() {
			ArgumentCaptor<Consumer<ScheduledTask>> captor = ArgumentCaptor.forClass(Consumer.class);
			verify(entityScheduler).run(eq(plugin), captor.capture(), isNull());
			return captor.getValue();
		}

		@SuppressWarnings("unchecked")
		private Consumer<ScheduledTask> captureAsyncRunNow() {
			ArgumentCaptor<Consumer<ScheduledTask>> captor = ArgumentCaptor.forClass(Consumer.class);
			verify(asyncScheduler).runNow(eq(plugin), captor.capture());
			return captor.getValue();
		}
	}
}
