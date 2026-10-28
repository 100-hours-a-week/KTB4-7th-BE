package com.memme.entity.store;

public enum MenuCategory {
    COFFEE("커피"),
    TEA("차"),
    BEVERAGE("음료"),
    CAKE("케이크"),
    BAKERY("베이커리"),
    OTHER("기타");

    private final String displayName;

    MenuCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
