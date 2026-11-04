package com.memme.service.sales;

import com.memme.entity.store.MenuCategory;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Conservative POS classification; unknown and mixed labels use the menu name as a fallback. */
public final class SalesMenuCategoryClassifier {

    private static final Pattern NON_COFFEE = Pattern.compile("non[ _-]*coffee|논커피|비커피");
    private static final Pattern MILK_TEA = Pattern.compile("밀크티|milk[ ]*tea");
    private static final Map<MenuCategory, Pattern> RULES = loadRules();

    private SalesMenuCategoryClassifier() {
    }

    public static MenuCategory classify(String rawCategory, String menuName) {
        EnumSet<MenuCategory> categories = matches(rawCategory);
        if (categories.size() == 1) {
            return categories.iterator().next();
        }
        EnumSet<MenuCategory> menuCategories = matches(menuName);
        // A flavour name (coffee cake, green-tea bread) does not change the food category.
        EnumSet<MenuCategory> foodCategories = EnumSet.copyOf(menuCategories);
        foodCategories.retainAll(EnumSet.of(MenuCategory.CAKE, MenuCategory.BAKERY));
        if (foodCategories.size() == 1) {
            return foodCategories.iterator().next();
        }
        return menuCategories.size() == 1 ? menuCategories.iterator().next() : MenuCategory.OTHER;
    }

    private static EnumSet<MenuCategory> matches(String value) {
        String normalized = NON_COFFEE.matcher(TossPosTextNormalizer.category(value)).replaceAll("non");
        normalized = MILK_TEA.matcher(normalized).replaceAll("tea");
        EnumSet<MenuCategory> matches = EnumSet.noneOf(MenuCategory.class);
        for (var rule : RULES.entrySet()) {
            if (rule.getValue().matcher(normalized).find()) {
                matches.add(rule.getKey());
            }
        }
        return matches;
    }

    private static Map<MenuCategory, Pattern> loadRules() {
        var stream = SalesMenuCategoryClassifier.class.getResourceAsStream("/sales/menu-category-rules.properties");
        if (stream == null) {
            throw new IllegalStateException("매출 카테고리 분류 규칙을 찾을 수 없습니다.");
        }
        Properties properties = new Properties();
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("매출 카테고리 분류 규칙을 읽을 수 없습니다.", exception);
        }
        Map<MenuCategory, Pattern> rules = new EnumMap<>(MenuCategory.class);
        for (MenuCategory category : MenuCategory.values()) {
            if (category != MenuCategory.OTHER) {
                rules.put(category, Pattern.compile(properties.getProperty(category.name())));
            }
        }
        return Map.copyOf(rules);
    }
}
