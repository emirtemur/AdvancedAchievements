package com.hm.achievement.db;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;

import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.lifecycle.Cleanable;
import com.hm.achievement.scheduler.TaskScheduler;

/**
 * Class used to provide a cache wrapper for various database statistics, in order to reduce load of database and enable
 * faster in-memory operations.
 *
 * @author Pyves
 *
 */
@Singleton
public class CacheManager implements Cleanable {

	private final TaskScheduler taskScheduler;
	private final AbstractDatabaseManager databaseManager;
	// Statistics of the different players for normal achievements; keys in the inner maps correspond to UUIDs.
	private final Map<NormalAchievements, Map<UUID, CachedStatistic>> normalAchievementsToPlayerStatistics;
	// Statistics of the different players for multiple achievements; keys in the inner maps correspond to concatenated
	// UUIDs and block/entity/command identifiers.
	private final Map<MultipleAchievements, Map<SubcategoryUUID, CachedStatistic>> multipleAchievementsToPlayerStatistics;
	// Multimap corresponding to the different achievement names received by players.
	private final Map<UUID, Set<String>> receivedAchievementsCache;

	@Inject
	public CacheManager(TaskScheduler taskScheduler, AbstractDatabaseManager databaseManager) {
		this.taskScheduler = taskScheduler;
		this.databaseManager = databaseManager;
		normalAchievementsToPlayerStatistics = new EnumMap<>(NormalAchievements.class);
		multipleAchievementsToPlayerStatistics = new EnumMap<>(MultipleAchievements.class);
		receivedAchievementsCache = new ConcurrentHashMap<>();

		// ConcurrentHashMaps are necessary to guarantee thread safety.
		for (NormalAchievements normalAchievement : NormalAchievements.values()) {
			normalAchievementsToPlayerStatistics.put(normalAchievement, new ConcurrentHashMap<>());
		}
		for (MultipleAchievements multipleAchievement : MultipleAchievements.values()) {
			multipleAchievementsToPlayerStatistics.put(multipleAchievement, new ConcurrentHashMap<>());
		}
	}

	@Override
	public void cleanPlayerData() {
		receivedAchievementsCache.keySet().removeIf(player -> !Bukkit.getOfflinePlayer(player).isOnline());

		// Indicate to the relevant cached statistics that the player has disconnected.
		for (MultipleAchievements category : MultipleAchievements.values()) {
			for (Entry<SubcategoryUUID, CachedStatistic> cachedEntry : getHashMap(category).entrySet()) {
				if (!Bukkit.getOfflinePlayer(cachedEntry.getKey().getUUID()).isOnline()) {
					cachedEntry.getValue().signalPlayerDisconnection();
				}
			}
		}
		for (NormalAchievements category : NormalAchievements.values()) {
			for (Entry<UUID, CachedStatistic> cachedEntry : getHashMap(category).entrySet()) {
				if (!Bukkit.getOfflinePlayer(cachedEntry.getKey()).isOnline()) {
					cachedEntry.getValue().signalPlayerDisconnection();
				}
			}
		}
	}

	/**
	 * Removes the cached statistics that have been written to the database and for which the player is no longer
	 * connected. Can be called from an asyncrhonous thread.
	 */
	public void cleanStaleCaches() {
		for (MultipleAchievements category : MultipleAchievements.values()) {
			cleanStaleCache(getHashMap(category), SubcategoryUUID::getUUID);
		}
		for (NormalAchievements category : NormalAchievements.values()) {
			cleanStaleCache(getHashMap(category), uuid -> uuid);
		}
	}

	private <T> void cleanStaleCache(Map<T, CachedStatistic> categoryMap, Function<T, UUID> keyUuidMapper) {
		for (Entry<T, CachedStatistic> entry : categoryMap.entrySet()) {
			T key = entry.getKey();
			UUID uuid = keyUuidMapper.apply(key);
			CachedStatistic statistic = entry.getValue();
			if (statistic.didPlayerDisconnect() && statistic.isDatabaseConsistent()) {
				// Player was disconnected at some point in the recent past delegate cleaning to the main server thread
				// (global region thread with Folia).
				taskScheduler.runGlobal(() -> categoryMap.computeIfPresent(key, (k, current) -> {
					// Check again whether statistic has been written to the database. This is necessary to cover
					// cases where the player may have reconnected in the meantime. Running within computeIfPresent
					// excludes concurrent increments of the same entry (see incrementCachedStatistic).
					if (current != statistic) {
						return current;
					}
					if (statistic.isDatabaseConsistent()) {
						return null;
					}
					if (Bukkit.getPlayer(uuid) != null) {
						statistic.resetDisconnection();
					}
					return statistic;
				}));
			}
		}
	}

	/**
	 * Retrieves a HashMap for a NormalAchievement based on the category.
	 *
	 * @param category
	 * @return the map of cached statistics for a Normal category
	 */
	public Map<UUID, CachedStatistic> getHashMap(NormalAchievements category) {
		return normalAchievementsToPlayerStatistics.get(category);
	}

	/**
	 * Retrieves a HashMap for a MultipleAchievement based on the category.
	 *
	 * @param category
	 * @return the map of cached statistics for a Multiple category
	 */
	public Map<SubcategoryUUID, CachedStatistic> getHashMap(MultipleAchievements category) {
		return multipleAchievementsToPlayerStatistics.get(category);
	}

	/**
	 * Increases the statistic for a NormalAchievement by the given value and returns the updated statistic value. Calls
	 * the database if not found in the cache.
	 *
	 * @param category
	 * @param player
	 * @param value
	 * @return the updated statistic value
	 */
	public long getAndIncrementStatisticAmount(NormalAchievements category, UUID player, int value) {
		return incrementCachedStatistic(getHashMap(category), player,
				() -> databaseManager.getNormalAchievementAmount(player, category), value);
	}

	/**
	 * Increases the statistic for a MultipleAchievement by the given value and returns the updated statistic value.
	 * Calls the database if not found in the cache.
	 *
	 * @param category
	 * @param subcategory
	 * @param player
	 * @param value
	 * @return the updated statistic value
	 */
	public long getAndIncrementStatisticAmount(MultipleAchievements category, String subcategory, UUID player, int value) {
		SubcategoryUUID key = new SubcategoryUUID(subcategory, player);
		return incrementCachedStatistic(getHashMap(category), key,
				() -> databaseManager.getMultipleAchievementAmount(player, category, key.getSubcategory()), value);
	}

	/**
	 * Increments a cached statistic, loading it from the database if absent. Increments are applied within
	 * computeIfPresent, so that they can't be lost to a concurrent removal of the entry by cleanStaleCaches.
	 */
	private <K> long incrementCachedStatistic(Map<K, CachedStatistic> cache, K key, LongSupplier databaseLoader,
			int value) {
		while (true) {
			CachedStatistic statistic = cache.get(key);
			if (statistic == null) {
				// The database is queried outside of any map operation, which must remain short.
				cache.putIfAbsent(key, new CachedStatistic(databaseLoader.getAsLong(), true));
				continue;
			}
			if (value == 0) {
				return statistic.getValue();
			}
			long[] updatedValue = new long[1];
			if (cache.computeIfPresent(key, (k, current) -> {
				updatedValue[0] = current.increment(value);
				return current;
			}) != null) {
				return updatedValue[0];
			}
			// Entry removed concurrently, reload it.
		}
	}

	/**
	 * Retrieves several statistics from one multiple-achievement category. Missing cache entries are loaded with a
	 * single database query.
	 *
	 * @param category
	 * @param subcategories
	 * @param player
	 * @return mapping of requested subcategories to statistics
	 */
	public Map<String, Long> getMultipleAchievementAmounts(MultipleAchievements category,
			Collection<String> subcategories, UUID player) {
		Map<SubcategoryUUID, CachedStatistic> cache = getHashMap(category);
		boolean cacheMiss = subcategories.stream()
				.map(subcategory -> new SubcategoryUUID(subcategory, player))
				.anyMatch(key -> !cache.containsKey(key));
		if (cacheMiss) {
			long defaultValue = category == MultipleAchievements.JOBSREBORN ? 1L : 0L;
			Map<String, Long> storedAmounts = databaseManager.getMultipleAchievementAmounts(player, category);
			for (String subcategory : subcategories) {
				SubcategoryUUID key = new SubcategoryUUID(subcategory, player);
				long storedAmount = storedAmounts.getOrDefault(key.getSubcategory(), defaultValue);
				cache.putIfAbsent(key, new CachedStatistic(storedAmount, true));
			}
		}

		Map<String, Long> amounts = new HashMap<>();
		for (String subcategory : subcategories) {
			amounts.put(subcategory, getAndIncrementStatisticAmount(category, subcategory, player, 0));
		}
		return amounts;
	}

	/**
	 * Returns whether player has received a specific achievement.
	 *
	 * @param player
	 * @param name
	 * @return true if achievement received by player, false otherwise
	 */
	public boolean hasPlayerAchievement(UUID player, String name) {
		return getPlayerAchievements(player).contains(name);
	}

	public boolean hasCachedPlayerAchievements(UUID player) {
		return receivedAchievementsCache.containsKey(player);
	}

	/**
	 * Returns the achievement names received by a player.
	 *
	 * @param player
	 * @return the achievement names received by the player
	 */
	public Set<String> getPlayerAchievements(UUID player) {
		return receivedAchievementsCache.computeIfAbsent(player, this::loadPlayerAchievementNames);
	}

	private Set<String> loadPlayerAchievementNames(UUID player) {
		Set<String> achievementNames = ConcurrentHashMap.newKeySet();
		achievementNames.addAll(databaseManager.getPlayerAchievementNames(player));
		return achievementNames;
	}

	/**
	 * Adds an achievement to the achievement received cache and removes it from the not received cache. A call to
	 * {@link #hasPlayerAchievement(UUID, String)} is expected to have been made made beforehand for the same player.
	 *
	 * @param player
	 * @param achievementName
	 */
	public void registerNewlyReceivedAchievement(UUID player, String achievementName) {
		getPlayerAchievements(player).add(achievementName);
	}

	/**
	 * Removes achievements from the received achievement cache and adds them to the not received cache.
	 *
	 * @param player
	 * @param achievementNames
	 */
	public void removePreviouslyReceivedAchievements(UUID player, Collection<String> achievementNames) {
		getPlayerAchievements(player).removeAll(achievementNames);
	}

	/**
	 * Resets a player's statistics to 0.
	 *
	 * @param uuid
	 * @param categoriesWithSubcategories
	 */
	public void resetPlayerStatistics(UUID uuid, Collection<String> categoriesWithSubcategories) {
		categoriesWithSubcategories.forEach(categoryWithSubcategory -> {
			if (categoryWithSubcategory.contains(".")) {
				String category = StringUtils.substringBefore(categoryWithSubcategory, ".");
				String subcategory = StringUtils.substringAfter(categoryWithSubcategory, ".");
				SubcategoryUUID key = new SubcategoryUUID(subcategory, uuid);
				MultipleAchievements multipleCategory = NormalAchievements.BREWING.toString().equals(category)
						? MultipleAchievements.BREWINGRECIPES
						: MultipleAchievements.getByName(category);
				resetCachedStatistic(getHashMap(multipleCategory), key);
			} else {
				NormalAchievements category = NormalAchievements.getByName(categoryWithSubcategory);
				if (category == NormalAchievements.CONNECTIONS) {
					databaseManager.clearConnection(uuid);
				} else {
					resetCachedStatistic(getHashMap(category), uuid);
				}
			}
		});
	}

	private <K> void resetCachedStatistic(Map<K, CachedStatistic> cache, K key) {
		cache.compute(key, (k, statistic) -> {
			if (statistic == null) {
				return new CachedStatistic(0L, false);
			}
			statistic.setValue(0L);
			return statistic;
		});
	}

}
