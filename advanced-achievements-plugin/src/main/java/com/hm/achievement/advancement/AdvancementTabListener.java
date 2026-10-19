package com.hm.achievement.advancement;

import javax.inject.Inject;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.hm.achievement.scheduler.TaskScheduler;

public class AdvancementTabListener implements Listener {

	private final TaskScheduler taskScheduler;
	private final AdvancementManager advancementManager;

	@Inject
	public AdvancementTabListener(TaskScheduler taskScheduler, AdvancementManager advancementManager) {
		this.taskScheduler = taskScheduler;
		this.advancementManager = advancementManager;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		// 1 tick later so generation (if running) has time to register the parent
		Player player = event.getPlayer();
		taskScheduler.runForEntityLater(player, () -> advancementManager.ensureRootVisible(player), 1L);
	}
}
