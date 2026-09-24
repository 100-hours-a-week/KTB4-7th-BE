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
        assertEquals("입력값을 확인해 주세요.", response.getBody().message());
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

    @Test
    void 회원가입_임시정보_만료_예외는_410_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleSignupCompletionExpired(
                new SignupCompletionExpiredException(SignupCompletionExpiredException.Reason.SIGNUP_DRAFT)
        );

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals("회원가입 임시 정보가 만료되었습니다. 다시 가입해 주세요.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 사업자_인증_결과_만료_예외는_410_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleSignupCompletionExpired(
                new SignupCompletionExpiredException(SignupCompletionExpiredException.Reason.BUSINESS_VERIFICATION)
        );

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals("사업자 인증 결과가 만료되었습니다. 다시 인증해 주세요.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 로그인_실패_예외는_401_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidLogin(new InvalidLoginException());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("이메일 또는 비밀번호가 올바르지 않습니다.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 현재_비밀번호_불일치_예외는_400_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleInvalidCurrentPassword(
                new InvalidCurrentPasswordException()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("현재 비밀번호가 일치하지 않습니다.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 비밀번호_수정_입력값_예외는_400_fieldErrors_응답으로_변환한다() {
        ResponseEntity<ApiResponse<FieldErrors>> response = exceptionHandler.handleInvalidPasswordChangeRequest(
                new InvalidPasswordChangeRequestException(List.of(
                        new FieldError("newPassword", "현재 비밀번호와 다르게 입력해 주세요.")
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("newPassword", response.getBody().data().fieldErrors().getFirst().field());
    }
}
