package com.hm.achievement.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.hm.achievement.category.Category;
import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.utils.StringHelper;

@Singleton
public class AchievementMap {

	// The live index is replaced as a whole on reload, so that readers on other threads (region threads with Folia)
	// never observe a partially rebuilt map. put() mutates the current index and is only meant for building a map
	// that isn't being read concurrently, such as the one parsed before a reload.
	private volatile Index index = new Index();

	@Inject
	public AchievementMap() {
	}

	public void put(Achievement achievement) {
		index.put(achievement);
	}

	public Collection<Achievement> getAll() {
		return index.namesToAchievements.values();
	}

	public Collection<String> getAllNames() {
		return index.namesToAchievements.keySet();
	}

	public Collection<String> getAllSanitisedDisplayNames() {
		return index.sanitisedDisplayNamesToAchievements.keySet();
	}

	public void clearAll() {
		index = new Index();
	}

	public void replaceWith(AchievementMap replacement) {
		Index replacementIndex = new Index();
		// Sort before re-inserting: getAll() is backed by a HashMap and is therefore unordered, while put() appends to
		// the lists consumers read in order. Sorting by subcategory first keeps a category's achievements contiguous
		// per subcategory, which AdvancementManager relies on to find advancement-chain boundaries; sorting by
		// threshold within a subcategory is what StatisticIncreaseHandler's early exit expects.
		replacement.getAll().stream()
				.sorted(Comparator.comparing((Achievement achievement) -> Objects.toString(achievement.getSubcategory(), ""))
						.thenComparingLong(Achievement::getThreshold))
				.forEach(replacementIndex::put);
		index = replacementIndex;
	}

	public Achievement getForName(String name) {
		return index.namesToAchievements.get(name);
	}

	public Achievement getForDisplayName(String displayName) {
		return index.sanitisedDisplayNamesToAchievements.get(sanitise(displayName));
	}

	public List<Achievement> getForCategory(Category category) {
		return index.categoriesToAchievements.getOrDefault(category, Collections.emptyList());
	}

	public List<Achievement> getForCategoryAndSubcategory(Category category, String subcategory) {
		String key = category instanceof NormalAchievements && subcategory.isEmpty()
				? category.toString()
				: category + "." + subcategory;
		return index.categoriesSubcategoriesToAchievements.getOrDefault(key, Collections.emptyList());
	}

	public Set<String> getSubcategoriesForCategory(Category category) {
		return index.categoriesToSubcategories.getOrDefault(category, Collections.emptySet());
	}

	public Set<String> getCategorySubcategories() {
		return index.categoriesSubcategoriesToAchievements.keySet();
	}

	private static String sanitise(String displayName) {
		return StringHelper.removeFormattingCodes(displayName).toLowerCase();
	}

	private static final class Index {

		private final Map<String, Achievement> namesToAchievements = new HashMap<>();
		private final Map<String, Achievement> sanitisedDisplayNamesToAchievements = new HashMap<>();
		private final Map<Category, List<Achievement>> categoriesToAchievements = new HashMap<>();
		private final Map<String, List<Achievement>> categoriesSubcategoriesToAchievements = new HashMap<>();
		private final Map<Category, Set<String>> categoriesToSubcategories = new HashMap<>();

		private void put(Achievement achievement) {
			namesToAchievements.put(achievement.getName(), achievement);
			sanitisedDisplayNamesToAchievements.put(sanitise(achievement.getDisplayName()), achievement);
			Category category = achievement.getCategory();
			String subcategory = Objects.toString(achievement.getSubcategory(), "");
			categoriesToAchievements.computeIfAbsent(category, c -> new ArrayList<>()).add(achievement);
			categoriesToSubcategories.computeIfAbsent(category, c -> new HashSet<>()).add(subcategory);
			if (category instanceof NormalAchievements) {
				String key = subcategory.isEmpty()
						? category.toString()
						: category + "." + subcategory;
				categoriesSubcategoriesToAchievements.computeIfAbsent(key, c -> new ArrayList<>())
						.add(achievement);
			} else if (category instanceof MultipleAchievements) {
				categoriesSubcategoriesToAchievements
						.computeIfAbsent(category + "." + achievement.getSubcategory(), c -> new ArrayList<>())
						.add(achievement);
			}
		}
	}
}
