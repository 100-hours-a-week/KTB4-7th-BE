package com.memme.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.memme.dto.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void 중복_회원가입_예외는_409_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleDuplicateSignup(
                new DuplicateSignupException()
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("이미 가입된 이메일 또는 휴대폰 번호입니다.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 회원가입_입력값_예외는_422_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidSignupRequest(
                new InvalidSignupRequestException()
        );

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("입력값 또는 필수 약관 동의를 확인해 주세요.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 사업자등록번호_형식_예외는_400_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidBusinessNumber(
                new InvalidBusinessNumberException()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid_business_number", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 사업자_상태_부적합_예외는_422_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBusinessStatusNotEligible(
                new BusinessStatusNotEligibleException()
        );

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("business_status_not_eligible", response.getBody().message());
        assertNull(response.getBody().data());
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
}
