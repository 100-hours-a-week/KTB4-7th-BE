package com.memme.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.BusinessVerificationRequest;
import com.memme.dto.auth.BusinessVerificationResponse;
import com.memme.entity.auth.BusinessVerification;
import com.memme.exception.BusinessStatusNotEligibleException;
import com.memme.exception.BusinessVerificationFailedException;
import com.memme.exception.InvalidBusinessNumberException;
import com.memme.repository.auth.BusinessVerificationRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessVerificationServiceTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-09-20T01:00:00Z");

    @Mock
    private BusinessVerificationRepository businessVerificationRepository;

    @Mock
    private BusinessStatusClient businessStatusClient;

    private BusinessVerificationService businessVerificationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, KOREA_ZONE);
        businessVerificationService = new BusinessVerificationService(
                businessVerificationRepository,
                businessStatusClient,
                clock
        );
    }

    @Test
    void 정상_사업자는_인증_결과를_저장하고_십분_만료_응답을_반환한다() throws Exception {
        BusinessVerificationRequest request = new BusinessVerificationRequest("1234567890");
        when(businessStatusClient.isActive(request.businessRegNumber())).thenReturn(true);
        doAnswer(this::saveWithGeneratedId).when(businessVerificationRepository).save(any(BusinessVerification.class));

        BusinessVerificationResponse response = businessVerificationService.verify(request);

        assertEquals(1L, response.businessVerificationId());
        assertEquals(OffsetDateTime.ofInstant(NOW.plusSeconds(600), KOREA_ZONE), response.expiresAt());

        ArgumentCaptor<BusinessVerification> captor = ArgumentCaptor.forClass(BusinessVerification.class);
        verify(businessVerificationRepository).save(captor.capture());
        BusinessVerification saved = captor.getValue();
        assertEquals("1234567890", fieldValue(saved, "businessRegNumber"));
        assertEquals(LocalDateTime.ofInstant(NOW, KOREA_ZONE), fieldValue(saved, "verifiedAt"));
        assertEquals(LocalDateTime.ofInstant(NOW.plusSeconds(600), KOREA_ZONE), fieldValue(saved, "expiresAt"));
        assertEquals(LocalDateTime.ofInstant(NOW, KOREA_ZONE), fieldValue(saved, "createdAt"));
    }

    @Test
    void 사업자등록번호가_숫자_열자리가_아니면_400_예외를_던진다() {
        assertThrows(
                InvalidBusinessNumberException.class,
                () -> businessVerificationService.verify(new BusinessVerificationRequest("123-456-7890"))
        );

        verify(businessStatusClient, never()).isActive(any());
        verify(businessVerificationRepository, never()).save(any());
    }

    @Test
    void 휴폐업_또는_미등록_사업자는_422_예외를_던진다() {
        BusinessVerificationRequest request = new BusinessVerificationRequest("1234567890");
        when(businessStatusClient.isActive(request.businessRegNumber())).thenReturn(false);

        assertThrows(BusinessStatusNotEligibleException.class, () -> businessVerificationService.verify(request));

        verify(businessVerificationRepository, never()).save(any());
    }

    @Test
    void 국세청_연동에_실패하면_502_예외를_던진다() {
        BusinessVerificationRequest request = new BusinessVerificationRequest("1234567890");
        when(businessStatusClient.isActive(request.businessRegNumber())).thenThrow(new RuntimeException());

        assertThrows(BusinessVerificationFailedException.class, () -> businessVerificationService.verify(request));

        verify(businessVerificationRepository, never()).save(any());
    }

    private Object saveWithGeneratedId(InvocationOnMock invocation) throws Exception {
        BusinessVerification businessVerification = invocation.getArgument(0);
        Field field = BusinessVerification.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(businessVerification, 1L);
        return businessVerification;
    }

    private Object fieldValue(BusinessVerification businessVerification, String fieldName) throws Exception {
        Field field = BusinessVerification.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(businessVerification);
    }
}
