package com.hm.achievement;

import com.hm.achievement.exception.PluginLoadError;
import com.hm.achievement.lifecycle.PluginLoader;
import com.hm.achievement.scheduler.TaskScheduler;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class JobsEnableWatcher implements Listener {

	private final TaskScheduler taskScheduler;
	private final Provider<PluginLoader> loaderProvider;
	private final Logger log;

	@Inject
	public JobsEnableWatcher(TaskScheduler taskScheduler, Provider<PluginLoader> loaderProvider, Logger log) {
		this.taskScheduler = taskScheduler;
		this.loaderProvider = loaderProvider;
		this.log = log;
	}

	@EventHandler
	public void onPluginEnable(PluginEnableEvent e) {
		if (e.getPlugin().getName().equalsIgnoreCase("Jobs")) {
			taskScheduler.runGlobal(() -> {
				try {
					loaderProvider.get().loadAdvancedAchievements();
					log.info("[AdvancedAchievements] Jobs enabled; JobsReborn category now active.");
				} catch (PluginLoadError ex) {
					log.log(Level.SEVERE, "Could not enable JobsReborn category after Jobs enable:", ex);
				}
			});
		}
	}
}
