package com.memme.repository.sales;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemStatus;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.store.MenuCategory;
import com.memme.repository.sales.SalesOrderItemRepository.HourlySalesAggregate;
import com.memme.repository.sales.SalesOrderItemRepository.MenuSalesAggregate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SalesAnalysisAggregateRepositoryTest {

    private static final long STORE_ID = 1L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 1, 0, 0);

    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;

    @Autowired
    SalesAnalysisAggregateRepositoryTest(
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
    }

    @Test
    void 메뉴_분류별_합계가_상품_행을_직접_합산한_값과_같다() {
        List<SalesOrderItemEntity> inRange = seedRandomSales();

        Map<String, long[]> expected = new HashMap<>();
        for (SalesOrderItemEntity item : inRange) {
            if (item.getItemType() != SalesOrderItemType.MENU) {
                continue;
            }
            long[] total = expected.computeIfAbsent(
                    item.getMenuKey() + "|" + item.getStandardCategory().name(), ignored -> new long[2]);
            total[0] += item.getNetAmount();
            total[1] += item.getQuantity();
        }

        Map<String, long[]> actual = new HashMap<>();
        for (MenuSalesAggregate row : itemRepository.aggregateMenuSales(STORE_ID, START, END)) {
            actual.put(row.getMenuKey() + "|" + row.getStandardCategory(), new long[]{
                    row.getSalesAmount().longValue(), row.getQuantity().longValue()});
        }

        assertThat(actual).hasSameSizeAs(expected);
        expected.forEach((key, total) -> assertThat(actual.get(key)).as(key).containsExactly(total));
    }

    @Test
    void 날짜_시간별_합계가_모든_상품_유형을_포함해_직접_합산한_값과_같다() {
        List<SalesOrderItemEntity> inRange = seedRandomSales();
        Map<Long, SalesOrderEntity> orders = new HashMap<>();
        orderRepository.findAll().forEach(order -> orders.put(order.getId(), order));

        Map<String, Long> expected = new HashMap<>();
        for (SalesOrderItemEntity item : inRange) {
            LocalDateTime orderedAt = orders.get(item.getSalesOrderId()).getOrderedAt();
            expected.merge(orderedAt.toLocalDate() + "|" + orderedAt.getHour(), item.getNetAmount(), Long::sum);
        }

        Map<String, Long> actual = new HashMap<>();
        for (HourlySalesAggregate row : itemRepository.aggregateHourlySales(STORE_ID, START, END)) {
            actual.put(row.getSalesDate() + "|" + row.getSalesHour().intValue(), row.getSalesAmount().longValue());
        }

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void 그룹의_첫_상품은_주문ID와_상품ID_순서로_고른다() {
        SalesOrderEntity first = order(STORE_ID, "first", LocalDateTime.of(2026, 9, 2, 11, 0));
        SalesOrderEntity second = order(STORE_ID, "second", LocalDateTime.of(2026, 9, 1, 9, 0));
        SalesOrderItemEntity firstName = itemRepository.save(
                item(first.getId(), "americano", "아메리카노", MenuCategory.COFFEE, 1, 4_000, SalesOrderItemType.MENU));
        itemRepository.save(
                item(first.getId(), "americano", "아메리카노(R)", MenuCategory.COFFEE, 1, 4_000, SalesOrderItemType.MENU));
        itemRepository.save(
                item(second.getId(), "americano", "아메리카노(L)", MenuCategory.COFFEE, 1, 5_000, SalesOrderItemType.MENU));

        List<MenuSalesAggregate> rows = itemRepository.aggregateMenuSales(STORE_ID, START, END);

        assertThat(rows).hasSize(1);
        MenuSalesAggregate row = rows.getFirst();
        assertThat(row.getFirstOrderId()).isEqualTo(first.getId());
        assertThat(row.getFirstItemId()).isEqualTo(firstName.getId());
        assertThat(row.getMenuName()).isEqualTo("아메리카노");
        assertThat(row.getSalesAmount().longValue()).isEqualTo(13_000L);
        assertThat(row.getQuantity().longValue()).isEqualTo(3L);
    }

    @Test
    void 기간에_주문이_없으면_빈_결과를_돌려준다() {
        SalesOrderEntity outside = order(STORE_ID, "outside", LocalDateTime.of(2026, 10, 1, 0, 0));
        itemRepository.save(item(outside.getId(), "latte", "카페라떼", MenuCategory.COFFEE, 1, 5_000,
                SalesOrderItemType.MENU));

        assertThat(itemRepository.aggregateMenuSales(STORE_ID, START, END)).isEmpty();
        assertThat(itemRepository.aggregateHourlySales(STORE_ID, START, END)).isEmpty();
    }

    private List<SalesOrderItemEntity> seedRandomSales() {
        Random random = new Random(20261110L);
        String[] menus = {"americano", "latte", "tea", "cake", "bread"};
        MenuCategory[] categories = {MenuCategory.COFFEE, MenuCategory.COFFEE, MenuCategory.TEA,
                MenuCategory.CAKE, MenuCategory.BAKERY};
        SalesOrderItemType[] types = {SalesOrderItemType.MENU, SalesOrderItemType.MENU, SalesOrderItemType.MENU,
                SalesOrderItemType.DELIVERY_FEE, SalesOrderItemType.EVENT};
        List<SalesOrderItemEntity> inRange = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            long storeId = index % 10 == 0 ? 2L : STORE_ID;
            LocalDateTime orderedAt = LocalDateTime.of(2026, 8, 25, 0, 0)
                    .plusDays(random.nextInt(45))
                    .plusHours(random.nextInt(24))
                    .plusMinutes(random.nextInt(60));
            SalesOrderEntity order = order(storeId, "order-" + index, orderedAt);
            boolean counted = storeId == STORE_ID && !orderedAt.isBefore(START) && orderedAt.isBefore(END);
            int itemCount = 1 + random.nextInt(3);
            for (int line = 0; line < itemCount; line++) {
                int menu = random.nextInt(menus.length);
                MenuCategory category = menu == 0 && random.nextBoolean() ? MenuCategory.BEVERAGE : categories[menu];
                int quantity = 1 + random.nextInt(3);
                long linePrice = (random.nextInt(9) - 1) * 1_000L * quantity;
                SalesOrderItemEntity saved = itemRepository.save(item(order.getId(), menus[menu], menus[menu],
                        category, quantity, linePrice, types[random.nextInt(types.length)]));
                if (counted) {
                    inRange.add(saved);
                }
            }
        }
        return inRange;
    }

    private SalesOrderEntity order(long storeId, String posOrderNo, LocalDateTime orderedAt) {
        return orderRepository.save(SalesOrderEntity.create(
                10L, storeId, SalesOrderChannel.POS, posOrderNo, orderedAt, true));
    }

    private SalesOrderItemEntity item(
            Long orderId,
            String menuKey,
            String menuName,
            MenuCategory category,
            int quantity,
            long linePrice,
            SalesOrderItemType type
    ) {
        return SalesOrderItemEntity.create(new SalesOrderItemEntity.Values(
                orderId,
                null,
                SalesOrderItemStatus.COMPLETED,
                menuName,
                menuName,
                menuKey,
                null,
                category,
                null,
                quantity,
                linePrice,
                linePrice / quantity,
                0,
                null,
                0,
                null,
                0,
                linePrice,
                true,
                Math.round(linePrice / 11.0),
                type
        ));
    }
}
