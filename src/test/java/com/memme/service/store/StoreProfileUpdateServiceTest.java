package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.memme.dto.store.StoreProfileUpdateRequest;
import com.memme.dto.store.StoreProfileUpdateResponse;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.entity.store.BusinessVerification;
import com.memme.exception.store.InvalidStoreBusinessVerificationException;
import com.memme.exception.store.EmptyStoreProfileUpdateException;
import com.memme.exception.store.InvalidStoreProfileUpdateRequestException;
import com.memme.exception.store.BusinessVerificationExpiredException;
import com.memme.exception.store.DuplicateBusinessRegistrationNumberException;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.repository.store.BusinessVerificationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreProfileUpdateServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T14:00:00Z"), ZoneOffset.UTC);

    @Mock private StoreRepository storeRepository;
    @Mock private StoreBusinessHoursRepository storeBusinessHoursRepository;
    @Mock private BusinessVerificationRepository businessVerificationRepository;

    private StoreProfileUpdateService storeProfileUpdateService;

    @BeforeEach
    void setUp() {
        storeProfileUpdateService = new StoreProfileUpdateService(
                storeRepository, storeBusinessHoursRepository, businessVerificationRepository, CLOCK
        );
    }

    @Test
    void 매장_기본정보와_주간_영업시간을_수정하고_수정결과를_반환한다() throws Exception {
        Store store = store(10L);
        List<StoreBusinessHours> currentBusinessHours = businessHours(store);
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(storeBusinessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L))
                .thenReturn(currentBusinessHours);

        StoreProfileUpdateResponse response = storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                "수정된 맴매카페",
                new StoreProfileUpdateRequest.Address(null, "서울특별시 강남구 테헤란로 456", null),
                null,
                null,
                updatedBusinessHours()
        ));

        assertThat(store.getStoreName()).isEqualTo("수정된 맴매카페");
        assertThat(store.getPostalCode()).isEqualTo("06236");
        assertThat(store.getAddress()).isEqualTo("서울특별시 강남구 테헤란로 456");
        assertThat(store.getAddressDetail()).isEqualTo("101호");
        assertThat(response.store().storeName()).isEqualTo("수정된 맴매카페");
        assertThat(response.store().businessHours()).hasSize(7);
        assertThat(response.store().businessHours().get(6).isClosed()).isTrue();
        assertThat(response.store().businessHours().get(6).openTime()).isNull();
        assertThat(response.store().businessHours().get(6).closeTime()).isNull();
        verifyNoInteractions(businessVerificationRepository);
    }

    @Test
    void 인증된_사업자번호를_미등록_매장에_저장하고_인증결과를_사용처리한다() throws Exception {
        Store store = storeWithoutBusinessRegistrationNo(10L);
        List<StoreBusinessHours> currentBusinessHours = businessHours(store);
        BusinessVerification verification = businessVerification(
                42L, "1234567890", LocalDateTime.of(2026, 9, 24, 14, 10)
        );
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(storeBusinessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L))
                .thenReturn(currentBusinessHours);
        when(businessVerificationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(verification));
        when(storeRepository.existsByBusinessRegistrationNoAndIdNot("1234567890", 10L)).thenReturn(false);

        storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                null, null, "1234567890", 42L, null
        ));

        assertThat(store.getBusinessRegistrationNo()).isEqualTo("1234567890");
        assertThat(store.getBusinessVerifiedAt()).isEqualTo(verification.getVerifiedAt());
        assertThat(verification.isUsed()).isTrue();
    }

    @Test
    void 새_인증을_통과하면_기존_사업자번호를_변경한다() throws Exception {
        Store store = store(10L);
        BusinessVerification verification = businessVerification(
                42L, "9876543210", LocalDateTime.of(2026, 9, 24, 14, 10)
        );
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(businessVerificationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(verification));
        when(storeRepository.existsByBusinessRegistrationNoAndIdNot("9876543210", 10L)).thenReturn(false);
        when(storeBusinessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(businessHours(store));

        storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                null, null, "9876543210", 42L, null
        ));

        assertThat(store.getBusinessRegistrationNo()).isEqualTo("9876543210");
        assertThat(store.getBusinessVerifiedAt()).isEqualTo(verification.getVerifiedAt());
        assertThat(verification.isUsed()).isTrue();
    }

    @Test
    void 사업자번호와_인증_ID는_함께_전달해야한다() {
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store(10L)));

        InvalidStoreProfileUpdateRequestException exception = assertThrows(
                InvalidStoreProfileUpdateRequestException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, "1234567890", null, null
                ))
        );

        assertThat(exception.getFieldErrors()).extracting(fieldError -> fieldError.field())
                .containsExactly("businessVerificationId");
    }

    @Test
    void 인증_ID만_전달해도_사업자번호를_함께_요청해야한다() {
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store(10L)));

        InvalidStoreProfileUpdateRequestException exception = assertThrows(
                InvalidStoreProfileUpdateRequestException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, null, 42L, null
                ))
        );

        assertThat(exception.getFieldErrors()).extracting(fieldError -> fieldError.field())
                .containsExactly("businessRegNumber");
    }

    @Test
    void 존재하지_않는_인증_ID는_거절한다() throws Exception {
        Store store = storeWithoutBusinessRegistrationNo(10L);
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(businessVerificationRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThrows(
                InvalidStoreBusinessVerificationException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, "1234567890", 42L, null
                ))
        );
    }

    @Test
    void 인증된_사업자번호와_요청한_번호가_다르면_거절한다() throws Exception {
        Store store = storeWithoutBusinessRegistrationNo(10L);
        BusinessVerification verification = businessVerification(
                42L, "9876543210", LocalDateTime.of(2026, 9, 24, 14, 10)
        );
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(businessVerificationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(verification));

        assertThrows(
                InvalidStoreBusinessVerificationException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, "1234567890", 42L, null
                ))
        );
        assertThat(verification.isUsed()).isFalse();
        assertThat(store.getBusinessRegistrationNo()).isNull();
    }

    @Test
    void 만료된_사업자_인증결과는_거절한다() throws Exception {
        Store store = storeWithoutBusinessRegistrationNo(10L);
        BusinessVerification verification = businessVerification(
                42L, "1234567890", LocalDateTime.of(2026, 9, 24, 14, 0)
        );
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(businessVerificationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(verification));

        assertThrows(
                BusinessVerificationExpiredException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, "1234567890", 42L, null
                ))
        );
    }

    @Test
    void 이미_사용한_사업자_인증결과는_재사용할_수_없다() throws Exception {
        Store store = storeWithoutBusinessRegistrationNo(10L);
        BusinessVerification verification = businessVerification(
                42L, "1234567890", LocalDateTime.of(2026, 9, 24, 14, 10)
        );
        verification.markUsedAt(LocalDateTime.of(2026, 9, 24, 13, 59));
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(businessVerificationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(verification));

        assertThrows(
                InvalidStoreBusinessVerificationException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, "1234567890", 42L, null
                ))
        );
    }

    @Test
    void 다른_매장에_등록된_사업자번호는_거절한다() throws Exception {
        Store store = storeWithoutBusinessRegistrationNo(10L);
        BusinessVerification verification = businessVerification(
                42L, "1234567890", LocalDateTime.of(2026, 9, 24, 14, 10)
        );
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(businessVerificationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(verification));
        when(storeRepository.existsByBusinessRegistrationNoAndIdNot("1234567890", 10L)).thenReturn(true);

        assertThrows(
                DuplicateBusinessRegistrationNumberException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, "1234567890", 42L, null
                ))
        );
        assertThat(verification.isUsed()).isFalse();
        assertThat(store.getBusinessRegistrationNo()).isNull();
    }

    @Test
    void 동시_등록으로_유니크_제약에_걸려도_중복_사업자번호_409로_처리한다() throws Exception {
        Store store = storeWithoutBusinessRegistrationNo(10L);
        BusinessVerification verification = businessVerification(
                42L, "1234567890", LocalDateTime.of(2026, 9, 24, 14, 10)
        );
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(businessVerificationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(verification));
        when(storeRepository.existsByBusinessRegistrationNoAndIdNot("1234567890", 10L)).thenReturn(false);
        when(storeBusinessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(businessHours(store));
        doThrow(new DataIntegrityViolationException("duplicate key", new ConstraintViolationException(
                "duplicate key", null, "uk_stores_business_registration_no"
        ))).when(storeRepository).flush();

        assertThrows(
                DuplicateBusinessRegistrationNumberException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, "1234567890", 42L, null
                ))
        );
    }

    @Test
    void 변경할_값이_없으면_빈_수정_예외를_던진다() {
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store(10L)));

        assertThrows(
                EmptyStoreProfileUpdateException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null, null, null, null, null
                ))
        );
    }

    @Test
    void 주소_객체의_모든_값이_비어있으면_빈_수정_예외를_던진다() {
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store(10L)));

        assertThrows(
                EmptyStoreProfileUpdateException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null,
                        new StoreProfileUpdateRequest.Address(null, null, null),
                        null,
                        null,
                        null
                ))
        );
    }

    @Test
    void 영업시간에_중복_요일이_있으면_입력값_예외를_던진다() {
        Store store = store(10L);
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));

        InvalidStoreProfileUpdateRequestException exception = assertThrows(
                InvalidStoreProfileUpdateRequestException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new StoreProfileUpdateRequest.BusinessHours(
                                        DayOfWeek.MONDAY, false, "09:00", "18:00"
                                ),
                                new StoreProfileUpdateRequest.BusinessHours(
                                        DayOfWeek.MONDAY, false, "09:00", "18:00"
                                )
                        )
                ))
        );

        assertThat(exception.getFieldErrors()).extracting(fieldError -> fieldError.field())
                .containsExactly("businessHours");
    }

    @Test
    void 휴무일에_영업시간을_함께_보내면_입력값_예외를_던진다() {
        Store store = store(10L);
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));

        assertThrows(
                InvalidStoreProfileUpdateRequestException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new StoreProfileUpdateRequest.BusinessHours(
                                        DayOfWeek.MONDAY, true, "09:00", "18:00"
                                )
                        )
                ))
        );
    }

    @Test
    void 자정부터_자정까지는_24시간_영업으로_수정할_수_있다() {
        Store store = store(10L);
        List<StoreBusinessHours> currentBusinessHours = businessHours(store);
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(storeBusinessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L))
                .thenReturn(currentBusinessHours);

        StoreProfileUpdateResponse response = storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                null,
                null,
                null,
                null,
                updatedBusinessHoursWithMondayHours("00:00", "00:00")
        ));

        assertThat(response.store().businessHours().getFirst().openTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(response.store().businessHours().getFirst().closeTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void 자정이_아닌_동일_시각의_영업시간_수정은_422_예외를_던진다() {
        Store store = store(10L);
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));

        assertThrows(
                InvalidStoreProfileUpdateRequestException.class,
                () -> storeProfileUpdateService.updateProfile(1L, new StoreProfileUpdateRequest(
                        null,
                        null,
                        null,
                        null,
                        updatedBusinessHoursWithMondayHours("10:00", "10:00")
                ))
        );
    }

    private Store store(Long id) {
        User owner = User.create(
                "owner@memme.com", "encoded-password", "01012345678", LocalDateTime.of(2026, 9, 24, 9, 0)
        );
        Store store = Store.create(
                owner,
                "1234567890",
                LocalDateTime.of(2026, 9, 24, 9, 0),
                "맴매카페",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101호",
                LocalDateTime.of(2026, 9, 24, 9, 0)
        );
        return withId(store, id);
    }

    private Store storeWithoutBusinessRegistrationNo(Long id) {
        User owner = User.create(
                "owner@memme.com", "encoded-password", "01012345678", LocalDateTime.of(2026, 9, 24, 9, 0)
        );
        Store store = Store.create(
                owner,
                null,
                null,
                "맴매카페",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101호",
                LocalDateTime.of(2026, 9, 24, 9, 0)
        );
        return withId(store, id);
    }

    private BusinessVerification businessVerification(Long id, String businessRegNumber, LocalDateTime expiresAt)
            throws Exception {
        BusinessVerification verification = BusinessVerification.create(
                businessRegNumber,
                LocalDateTime.of(2026, 9, 24, 14, 0),
                expiresAt,
                LocalDateTime.of(2026, 9, 24, 14, 0)
        );
        return withId(verification, id);
    }

    private List<StoreBusinessHours> businessHours(Store store) {
        return List.of(
                businessHours(store, DayOfWeek.MONDAY),
                businessHours(store, DayOfWeek.TUESDAY),
                businessHours(store, DayOfWeek.WEDNESDAY),
                businessHours(store, DayOfWeek.THURSDAY),
                businessHours(store, DayOfWeek.FRIDAY),
                businessHours(store, DayOfWeek.SATURDAY),
                businessHours(store, DayOfWeek.SUNDAY)
        );
    }

    private StoreBusinessHours businessHours(Store store, DayOfWeek dayOfWeek) {
        return StoreBusinessHours.create(
                store,
                dayOfWeek.getValue(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                false,
                LocalDateTime.of(2026, 9, 24, 9, 0)
        );
    }

    private List<StoreProfileUpdateRequest.BusinessHours> updatedBusinessHours() {
        return List.of(
                updateBusinessHours(DayOfWeek.MONDAY, false),
                updateBusinessHours(DayOfWeek.TUESDAY, false),
                updateBusinessHours(DayOfWeek.WEDNESDAY, false),
                updateBusinessHours(DayOfWeek.THURSDAY, false),
                updateBusinessHours(DayOfWeek.FRIDAY, false),
                updateBusinessHours(DayOfWeek.SATURDAY, false),
                updateBusinessHours(DayOfWeek.SUNDAY, true)
        );
    }

    private List<StoreProfileUpdateRequest.BusinessHours> updatedBusinessHoursWithMondayHours(
            String openTime,
            String closeTime
    ) {
        return List.of(
                new StoreProfileUpdateRequest.BusinessHours(DayOfWeek.MONDAY, false, openTime, closeTime),
                updateBusinessHours(DayOfWeek.TUESDAY, false),
                updateBusinessHours(DayOfWeek.WEDNESDAY, false),
                updateBusinessHours(DayOfWeek.THURSDAY, false),
                updateBusinessHours(DayOfWeek.FRIDAY, false),
                updateBusinessHours(DayOfWeek.SATURDAY, false),
                updateBusinessHours(DayOfWeek.SUNDAY, true)
        );
    }

    private StoreProfileUpdateRequest.BusinessHours updateBusinessHours(DayOfWeek dayOfWeek, boolean isClosed) {
        return new StoreProfileUpdateRequest.BusinessHours(
                dayOfWeek,
                isClosed,
                isClosed ? null : "10:00",
                isClosed ? null : "20:00"
        );
    }

    private <T> T withId(T target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
            return target;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
