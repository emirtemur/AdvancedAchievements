package com.hm.achievement.scheduler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.hm.achievement.scheduler.TaskScheduler.Task;

/**
 * Runs repeating tasks for each online player on the thread owning that player. With Folia, this replaces global
 * timers iterating over all online players, which would have to hand every player over to their region thread.
 *
 * @author LucidAPs
 */
@Singleton
public class PerPlayerTasks implements Listener {

	private final TaskScheduler taskScheduler;
	private final List<Job> jobs = new CopyOnWriteArrayList<>();
	private final Map<UUID, List<Task>> playerTasks = new ConcurrentHashMap<>();

	@Inject
	public PerPlayerTasks(TaskScheduler taskScheduler) {
		this.taskScheduler = taskScheduler;
	}

	/**
	 * Starts a repeating task for every online player and for players joining later.
	 *
	 * @param taskFactory creates the task of a given player; the task may keep per-player state
	 * @param periodTicks
	 */
	public void schedule(Function<Player, Runnable> taskFactory, long periodTicks) {
		Job job = new Job(taskFactory, periodTicks);
		jobs.add(job);
		Bukkit.getOnlinePlayers().forEach(player -> start(player, job));
	}

	/**
	 * Cancels all the tasks scheduled by this class.
	 */
	public void cancelAll() {
		jobs.clear();
		playerTasks.values().forEach(tasks -> tasks.forEach(Task::cancel));
		playerTasks.clear();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		jobs.forEach(job -> start(event.getPlayer(), job));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		List<Task> tasks = playerTasks.remove(event.getPlayer().getUniqueId());
		if (tasks != null) {
			tasks.forEach(Task::cancel);
		}
	}

	private void start(Player player, Job job) {
		// Start one tick later, so that the first run happens on the thread owning the player.
		Task task = taskScheduler.runForEntityTimer(player, job.taskFactory.apply(player), 1L, job.periodTicks);
		playerTasks.computeIfAbsent(player.getUniqueId(), uuid -> new CopyOnWriteArrayList<>()).add(task);
	}

	private static final class Job {

		private final Function<Player, Runnable> taskFactory;
		private final long periodTicks;

		private Job(Function<Player, Runnable> taskFactory, long periodTicks) {
			this.taskFactory = taskFactory;
			this.periodTicks = periodTicks;
		}
	}
}
