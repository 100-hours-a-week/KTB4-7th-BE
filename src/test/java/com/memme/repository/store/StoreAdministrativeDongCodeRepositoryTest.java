package com.memme.repository.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.repository.auth.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StoreAdministrativeDongCodeRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 10, 0);

    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void 행정동_코드로_해당_코드가_저장된_매장만_조회한다() {
        Store matchingStore = createStore("dong-one@example.com", "01011112222", "1168051000");
        createStore("dong-two@example.com", "01033334444", "1171051000");
        createStore("dong-unassigned@example.com", "01055556666", null);
        entityManager.flush();
        entityManager.clear();

        assertThat(storeRepository.findAllByAdministrativeDongCode("1168051000"))
                .extracting(Store::getId)
                .containsExactly(matchingStore.getId());
    }

    private Store createStore(String email, String phone, String administrativeDongCode) {
        User owner = userRepository.save(User.create(email, "encoded-password", phone, NOW));
        Store store = Store.create(owner, null, null, "맴매카페", "06236",
                "서울특별시 강남구 테헤란로 123", null, NOW);
        if (administrativeDongCode != null) {
            store.updateAdministrativeDongCode(administrativeDongCode);
        }
        return storeRepository.save(store);
    }
}
