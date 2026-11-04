package com.memme.service.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.store.Menu;
import com.memme.entity.store.MenuCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

class SalesMenuCategoryClassifierTest {

    @ParameterizedTest
    @CsvFileSource(resources = "/sales/menu-category-cases.csv", numLinesToSkip = 1)
    void 매장별_라벨과_메뉴명을_공통_카테고리로_분류한다(
            String rawCategory, String menuName, MenuCategory expected) {
        assertThat(SalesMenuCategoryClassifier.classify(rawCategory, menuName)).isEqualTo(expected);
    }

    @Test
    void 원본과_메뉴명이_모두_없어도_기타로_분류한다() {
        assertThat(SalesMenuCategoryClassifier.classify(null, null)).isEqualTo(MenuCategory.OTHER);
    }

    @Test
    void 메뉴판_파서_매출저장이_동일한_6종_enum을_사용한다() throws Exception {
        assertThat(MenuCategory.values()).extracting(Enum::name)
                .containsExactly("COFFEE", "TEA", "BEVERAGE", "CAKE", "BAKERY", "OTHER");
        assertThat(Menu.class.getDeclaredField("category").getType()).isEqualTo(MenuCategory.class);
        assertThat(SalesOrderItem.class.getDeclaredField("category").getType()).isEqualTo(MenuCategory.class);
        assertThat(SalesOrderItemEntity.class.getDeclaredField("standardCategory").getType())
                .isEqualTo(MenuCategory.class);
    }
}
