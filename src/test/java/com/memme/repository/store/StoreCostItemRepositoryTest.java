package com.memme.repository.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreCostItem;
import com.memme.repository.auth.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StoreCostItemRepositoryTest {

    @Autowired private StoreCostItemRepository costItemRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void 매장과_비용_기준월로_조회한다() {
        Long storeId = createStore();
        LocalDate month = LocalDate.of(2026, 10, 1);
        costItemRepository.saveAndFlush(cost(storeId, month));
        entityManager.clear();

        assertThat(costItemRepository.findByStoreIdAndCostMonth(storeId, month))
                .get()
                .extracting(StoreCostItem::getRentAmount)
                .isEqualTo(1_500_000L);
        assertThat(costItemRepository.findByStoreIdAndCostMonth(storeId, month.plusMonths(1))).isEmpty();
    }

    @Test
    void 같은_매장의_같은_월은_두_행을_저장할_수_없다() {
        Long storeId = createStore();
        LocalDate month = LocalDate.of(2026, 10, 1);
        costItemRepository.saveAndFlush(cost(storeId, month));

        assertThatThrownBy(() -> costItemRepository.saveAndFlush(cost(storeId, month)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 여러_월의_비용을_날짜순으로_조회한다() {
        Long storeId = createStore();
        costItemRepository.saveAndFlush(cost(storeId, LocalDate.of(2026, 9, 1)));
        costItemRepository.saveAndFlush(cost(storeId, LocalDate.of(2026, 10, 1)));
        costItemRepository.saveAndFlush(cost(storeId, LocalDate.of(2026, 11, 1)));
        entityManager.clear();

        assertThat(costItemRepository.findAllByStoreIdAndCostMonthBetweenOrderByCostMonthAsc(
                storeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
                .extracting(StoreCostItem::getCostMonth)
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
    }

    @Test
    void 대상_월보다_이전의_가장_최근_비용을_조회한다() {
        Long storeId = createStore();
        costItemRepository.saveAndFlush(cost(storeId, LocalDate.of(2026, 8, 1)));
        costItemRepository.saveAndFlush(cost(storeId, LocalDate.of(2026, 10, 1)));
        entityManager.clear();

        assertThat(costItemRepository.findFirstByStoreIdAndCostMonthLessThanOrderByCostMonthDesc(
                storeId, LocalDate.of(2026, 11, 1)))
                .get().extracting(StoreCostItem::getCostMonth)
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(costItemRepository.findFirstByStoreIdAndCostMonthLessThanOrderByCostMonthDesc(
                storeId, LocalDate.of(2026, 8, 1))).isEmpty();
    }

    private Long createStore() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 9, 0);
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        User owner = userRepository.saveAndFlush(User.create(
                "cost-" + suffix + "@example.com", "encoded-password", "010" + suffix.replaceAll("[^0-9]", "0")
                        .substring(0, 8), now));
        return storeRepository.saveAndFlush(Store.create(owner, null, null,
                "맴매카페", "06236", "서울특별시 강남구 테헤란로 1", null, now)).getId();
    }

    private StoreCostItem cost(Long storeId, LocalDate month) {
        return StoreCostItem.create(storeId, month, 1_500_000, 3_000_000,
                new BigDecimal("0.325"), LocalDateTime.of(2026, 10, 7, 9, 0));
    }
}
