package com.hm.achievement.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.hm.achievement.scheduler.TaskScheduler.Task;

class PerPlayerTasksTest {

	private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
	private final PerPlayerTasks underTest = new PerPlayerTasks(taskScheduler);

	@Test
	void shouldStartTasksForOnlineAndJoiningPlayers() {
		Player online = player();
		Player joining = player();
		Runnable onlineTask = mock(Runnable.class);
		Runnable joiningTask = mock(Runnable.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getOnlinePlayers).then(invocation -> List.of(online));
			underTest.schedule(player -> player == online ? onlineTask : joiningTask, 200L);
		}
		underTest.onPlayerJoin(new PlayerJoinEvent(joining, "joined"));

		verify(taskScheduler).runForEntityTimer(online, onlineTask, 1L, 200L);
		verify(taskScheduler).runForEntityTimer(joining, joiningTask, 1L, 200L);
	}

	@Test
	void shouldCancelTasksWhenPlayerQuits() {
		Player player = player();
		Task task = mock(Task.class);
		when(taskScheduler.runForEntityTimer(eq(player), any(), eq(1L), eq(20L))).thenReturn(task);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getOnlinePlayers).then(invocation -> List.of(player));
			underTest.schedule(p -> mock(Runnable.class), 20L);
		}

		underTest.onPlayerQuit(new PlayerQuitEvent(player, "left"));

		verify(task).cancel();
	}

	@Test
	void cancelAllShouldStopTasksAndForgetJobs() {
		Player player = player();
		Task task = mock(Task.class);
		when(taskScheduler.runForEntityTimer(eq(player), any(), eq(1L), eq(20L))).thenReturn(task);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getOnlinePlayers).then(invocation -> List.of(player));
			underTest.schedule(p -> mock(Runnable.class), 20L);
		}

		underTest.cancelAll();
		Player joining = player();
		underTest.onPlayerJoin(new PlayerJoinEvent(joining, "joined"));

		verify(task).cancel();
		verify(taskScheduler, never()).runForEntityTimer(eq(joining), any(), eq(1L), eq(20L));
	}

	@Test
	void shouldDoNothingWithoutJobs() {
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.emptyList());
			underTest.onPlayerJoin(new PlayerJoinEvent(player(), "joined"));
		}

		verify(taskScheduler, never()).runForEntityTimer(any(), any(), eq(1L), eq(20L));
	}

	private Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}
}
