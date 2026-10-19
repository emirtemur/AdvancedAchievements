package com.hm.achievement.scheduler;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.hm.achievement.AdvancedAchievements;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Schedules plugin tasks on the threads that own the data they touch. On Folia, work is dispatched to the global
 * region, entity or async schedulers; on other servers, the regular Bukkit scheduler is used and behaviour is
 * unchanged.
 *
 * @author LucidAPs
 */
@Singleton
public class TaskScheduler {

	private static final String FOLIA_REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer";
	private static final long MILLIS_PER_TICK = 50L;
	private static final Task NO_OP_TASK = () -> {};

	private final Plugin plugin;
	private final boolean folia;

	@Inject
	public TaskScheduler(AdvancedAchievements advancedAchievements) {
		this(advancedAchievements, isFoliaServer());
	}

	public TaskScheduler(Plugin plugin, boolean folia) {
		this.plugin = plugin;
		this.folia = folia;
	}

	/**
	 * Determines whether the running server uses Folia's regionised multithreading.
	 *
	 * @return true if running on Folia, false otherwise
	 */
	public static boolean isFoliaServer() {
		try {
			Class.forName(FOLIA_REGIONIZED_SERVER);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	public boolean isFolia() {
		return folia;
	}

	/**
	 * Runs a task on the main thread, or on the global region thread with Folia.
	 *
	 * @param task
	 */
	public void runGlobal(Runnable task) {
		if (folia) {
			Bukkit.getGlobalRegionScheduler().execute(plugin, task);
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}

	/**
	 * Runs a task that performs server-wide operations, such as dispatching console commands. Without Folia, the task
	 * runs immediately if called from the main thread and is scheduled on it otherwise; with Folia, it is handed over
	 * to the global region thread.
	 *
	 * @param task
	 */
	public void runOnGlobalThread(Runnable task) {
		if (!folia && Bukkit.isPrimaryThread()) {
			task.run();
		} else {
			runGlobal(task);
		}
	}

	public Task runGlobalLater(Runnable task, long delayTicks) {
		if (folia) {
			return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), atLeastOneTick(delayTicks)));
		}
		return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
	}

	public Task runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
		if (folia) {
			return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(),
					atLeastOneTick(delayTicks), atLeastOneTick(periodTicks)));
		}
		return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
	}

	public void runAsync(Runnable task) {
		if (folia) {
			Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
		} else {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
		}
	}

	public Task runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
		if (folia) {
			return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
					Math.max(0L, delayTicks) * MILLIS_PER_TICK, atLeastOneTick(periodTicks) * MILLIS_PER_TICK,
					TimeUnit.MILLISECONDS));
		}
		return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
	}

	/**
	 * Schedules a task on the thread owning the entity. With Folia, the task is dropped if the entity is removed (e.g.
	 * player disconnects) before it runs: callers must only schedule work that is meaningless without the entity, or
	 * that is retried later (statistics are persisted independently, so thresholds are checked again on the next
	 * increase).
	 *
	 * @param entity
	 * @param task
	 */
	public void runForEntity(Entity entity, Runnable task) {
		if (folia) {
			entity.getScheduler().run(plugin, t -> task.run(), null);
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}

	/**
	 * Runs a task immediately if the current thread owns the entity (the main thread without Folia), otherwise
	 * schedules it on the owning thread.
	 *
	 * @param entity
	 * @param task
	 */
	public void runOnEntityThread(Entity entity, Runnable task) {
		runOnEntityThread(entity, task, null);
	}

	/**
	 * Same as {@link #runOnEntityThread(Entity, Runnable)}, with a callback invoked instead of the task if the entity
	 * is removed before the task could run with Folia. The callback must not interact with the entity's world.
	 *
	 * @param entity
	 * @param task
	 * @param retired
	 */
	public void runOnEntityThread(Entity entity, Runnable task, Runnable retired) {
		if (folia ? Bukkit.isOwnedByCurrentRegion(entity) : Bukkit.isPrimaryThread()) {
			task.run();
		} else if (folia) {
			if (entity.getScheduler().run(plugin, t -> task.run(), retired) == null && retired != null) {
				// Entity already removed: the retired callback isn't invoked by Folia in this case.
				retired.run();
			}
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}

	/**
	 * Schedules a repeating task on the thread owning the entity. With Folia, the task stops when the entity is
	 * removed.
	 *
	 * @param entity
	 * @param task
	 * @param delayTicks
	 * @param periodTicks
	 * @return the scheduled task
	 */
	public Task runForEntityTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
		if (folia) {
			return wrap(entity.getScheduler().runAtFixedRate(plugin, t -> task.run(), null, atLeastOneTick(delayTicks),
					atLeastOneTick(periodTicks)));
		}
		return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
	}

	public void runForEntityLater(Entity entity, Runnable task, long delayTicks) {
		if (folia) {
			entity.getScheduler().runDelayed(plugin, t -> task.run(), null, atLeastOneTick(delayTicks));
		} else {
			Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
		}
	}

	/**
	 * Schedules a task on the thread that should interact with a command sender: the owning region for entities (such
	 * as players), the global region otherwise (such as the console).
	 *
	 * @param sender
	 * @param task
	 */
	public void runForSender(CommandSender sender, Runnable task) {
		if (sender instanceof Entity) {
			runForEntity((Entity) sender, task);
		} else {
			runGlobal(task);
		}
	}

	private static long atLeastOneTick(long ticks) {
		// Folia's region and entity schedulers reject delays and periods lower than one tick.
		return Math.max(1L, ticks);
	}

	private static Task wrap(BukkitTask task) {
		return task == null ? NO_OP_TASK : task::cancel;
	}

	private static Task wrap(ScheduledTask task) {
		return task == null ? NO_OP_TASK : task::cancel;
	}

	/**
	 * Handle on a scheduled task, independent of the underlying scheduler implementation.
	 */
	@FunctionalInterface
	public interface Task {

		void cancel();
	}
}
