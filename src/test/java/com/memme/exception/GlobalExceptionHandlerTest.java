package com.memme.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.memme.dto.common.ApiResponse;
import com.memme.dto.common.FieldError;
import com.memme.dto.common.FieldErrors;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void 중복_회원가입_예외는_409_응답으로_변환한다() {
        ResponseEntity<ApiResponse<FieldErrors>> response = exceptionHandler.handleDuplicateSignup(
                new DuplicateSignupException(List.of(
                        new FieldError("email", "이미 사용 중인 이메일입니다.")
                ))
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("이미 가입된 이메일 또는 휴대폰 번호입니다.", response.getBody().message());
        assertNotNull(response.getBody().data());
        assertEquals("email", response.getBody().data().fieldErrors().getFirst().field());
    }

    @Test
    void 회원가입_입력값_예외는_422_응답으로_변환한다() {
        ResponseEntity<ApiResponse<FieldErrors>> response = exceptionHandler.handleInvalidSignupRequest(
                new InvalidSignupRequestException(List.of(
                        new FieldError("passwordConfirm", "비밀번호와 일치하지 않습니다.")
                ))
        );

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("입력값을 확인해 주세요.", response.getBody().message());
        assertNotNull(response.getBody().data());
        assertEquals("passwordConfirm", response.getBody().data().fieldErrors().getFirst().field());
    }

    @Test
    void 사업자_상태_부적합_예외는_422_응답으로_변환한다() {
        ResponseEntity<ApiResponse<FieldErrors>> response = exceptionHandler.handleBusinessStatusNotEligible(
                new BusinessStatusNotEligibleException()
        );

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("business_status_not_eligible", response.getBody().message());
        assertNotNull(response.getBody().data());
        assertEquals("businessRegNumber", response.getBody().data().fieldErrors().getFirst().field());
    }

    @Test
    void 국세청_연동_실패_예외는_502_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBusinessVerificationFailed(
                new BusinessVerificationFailedException()
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("business_verification_failed", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 주소_검색_외부_API_실패_예외는_502_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAddressSearchFailed(
                new AddressSearchFailedException()
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("주소 검색 서비스를 이용할 수 없습니다. 다시 시도해주세요.", response.getBody().message());
        assertNull(response.getBody().data());
    }
}
