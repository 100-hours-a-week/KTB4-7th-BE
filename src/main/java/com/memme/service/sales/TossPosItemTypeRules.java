package com.memme.service.sales;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class TossPosItemTypeRules {

    private static final Set<SalesItemType> CLASSIFICATION_ORDER = EnumSet.of(
        SalesItemType.PARKING,
        SalesItemType.PREPAID_CARD,
        SalesItemType.DELIVERY_FEE,
        SalesItemType.PLATFORM_PLACEHOLDER,
        SalesItemType.EVENT,
        SalesItemType.GOODS
    );

    private final Map<SalesItemType, Set<String>> menuKeysByType;

    public TossPosItemTypeRules(
        Map<SalesItemType, ? extends Collection<String>> itemNamesByType
    ) {
        Objects.requireNonNull(itemNamesByType, "itemNamesByType");
        EnumMap<SalesItemType, Set<String>> normalized = new EnumMap<>(SalesItemType.class);

        for (SalesItemType itemType : CLASSIFICATION_ORDER) {
            Collection<String> names = itemNamesByType.get(itemType);
            if (names == null) {
                names = Set.of();
            }
            Set<String> menuKeys = names.stream()
                .map(TossPosTextNormalizer::menuKey)
                .filter(key -> !key.isBlank())
                .collect(Collectors.toUnmodifiableSet());
            normalized.put(itemType, menuKeys);
        }

        if (itemNamesByType.containsKey(SalesItemType.MENU)) {
            throw new IllegalArgumentException("MENU는 비메뉴 분류 설정에 포함할 수 없습니다.");
        }
        this.menuKeysByType = Map.copyOf(normalized);
    }

    public static TossPosItemTypeRules menuOnly() {
        return new TossPosItemTypeRules(Map.of());
    }

    SalesItemType classify(String menuKey) {
        for (SalesItemType itemType : CLASSIFICATION_ORDER) {
            if (menuKeysByType.get(itemType).contains(menuKey)) {
                return itemType;
            }
        }
        return SalesItemType.MENU;
    }
}
