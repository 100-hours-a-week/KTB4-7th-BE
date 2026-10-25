package com.memme.exception;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.dto.common.ApiResponse;
import com.memme.dto.common.FieldError;
import com.memme.dto.common.FieldErrors;
import com.memme.exception.auth.DuplicateSignupException;
import com.memme.exception.auth.InvalidCurrentPasswordException;
import com.memme.exception.auth.InvalidLoginException;
import com.memme.exception.auth.LoginRateLimitExceededException;
import com.memme.exception.auth.InvalidPasswordChangeRequestException;
import com.memme.exception.auth.InvalidSignupRequestException;
import com.memme.exception.auth.PasswordResetRateLimitExceededException;
import com.memme.exception.auth.SignupCompletionExpiredException;
import com.memme.exception.store.AddressSearchFailedException;
import com.memme.exception.store.BusinessVerificationExpiredException;
import com.memme.exception.store.BusinessStatusNotEligibleException;
import com.memme.exception.store.BusinessVerificationFailedException;
import com.memme.exception.store.DuplicateBusinessRegistrationNumberException;
import com.memme.exception.store.EmptyStoreProfileUpdateException;
import com.memme.exception.store.InvalidStoreBusinessVerificationException;
import com.memme.exception.store.InvalidStoreProfileUpdateRequestException;
import com.memme.exception.store.StoreProfileNotFoundException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void 미처리_예외는_공통_500_응답으로_변환하고_내부정보를_숨긴다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UnhandledExceptionController())
                .setControllerAdvice(exceptionHandler)
                .build();

        var result = assertDoesNotThrow(() -> mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn());

        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains("RuntimeException"));
        assertFalse(responseBody.contains("sensitive internal detail"));
    }

    @Test
    void 트랜잭션_시작_중_DB_연결_획득_실패는_503과_Retry_After로_응답한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UnhandledExceptionController())
                .setControllerAdvice(exceptionHandler)
                .build();

        mockMvc.perform(get("/test/db-connection-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.RETRY_AFTER, "3"))
                .andExpect(jsonPath("$.message")
                        .value("서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    void 연결획득실패가_아닌_트랜잭션_시작_예외는_기존_공통_500_응답으로_유지한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UnhandledExceptionController())
                .setControllerAdvice(exceptionHandler)
                .build();

        mockMvc.perform(get("/test/transaction-start-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    void 다른_DB_예외는_기존_공통_500_응답으로_유지한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UnhandledExceptionController())
                .setControllerAdvice(exceptionHandler)
                .build();

        mockMvc.perform(get("/test/db-integrity-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    void 트랜잭션_밖의_DB_연결_획득_실패는_503과_Retry_After로_응답한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UnhandledExceptionController())
                .setControllerAdvice(exceptionHandler)
                .build();

        mockMvc.perform(get("/test/repository-db-connection-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.RETRY_AFTER, "3"))
                .andExpect(jsonPath("$.message")
                        .value("서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    void 연결획득실패가_아닌_DB_자원오류는_기존_공통_500_응답으로_유지한다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UnhandledExceptionController())
                .setControllerAdvice(exceptionHandler)
                .build();

        mockMvc.perform(get("/test/repository-db-resource-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @RestController
    private static class UnhandledExceptionController {

        @GetMapping("/test/unhandled")
        public void triggerUnhandledException() {
            throw new RuntimeException("sensitive internal detail");
        }

        @GetMapping("/test/db-connection-unavailable")
        public void triggerDbConnectionUnavailable() {
            throw new CannotCreateTransactionException(
                    "connection acquisition timeout",
                    new SQLTransientConnectionException("Hikari connection acquisition timed out")
            );
        }

        @GetMapping("/test/transaction-start-error")
        public void triggerTransactionStartError() {
            throw new CannotCreateTransactionException(
                    "transaction start failed",
                    new IllegalStateException("non-connection transaction failure")
            );
        }

        @GetMapping("/test/db-integrity-error")
        public void triggerDbIntegrityError() {
            throw new DataIntegrityViolationException("constraint violation");
        }

        @GetMapping("/test/repository-db-connection-unavailable")
        public void triggerRepositoryDbConnectionUnavailable() {
            throw new DataAccessResourceFailureException(
                    "JDBC connection unavailable",
                    new org.hibernate.exception.JDBCConnectionException(
                            "Unable to acquire JDBC connection",
                            new SQLTransientConnectionException("Hikari connection acquisition timed out")
                    )
            );
        }

        @GetMapping("/test/repository-db-resource-error")
        public void triggerRepositoryDbResourceError() {
            throw new DataAccessResourceFailureException(
                    "database resource unavailable",
                    new IllegalStateException("non-connection database failure")
            );
        }
    }

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
    void 비밀번호_재설정_제한_초과는_429와_retryAfterSeconds로_응답한다() {
        var response = exceptionHandler.handlePasswordResetRateLimitExceeded(
                new PasswordResetRateLimitExceededException(120)
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", response.getBody().message());
        assertNull(response.getBody().data());
        assertEquals(120, response.getBody().retryAfterSeconds());
    }

    @Test
    void 로그인_제한_초과는_일반적인_429와_retryAfterSeconds로_응답한다() {
        var response = exceptionHandler.handleLoginRateLimitExceeded(new LoginRateLimitExceededException(30));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", response.getBody().message());
        assertNull(response.getBody().data());
        assertEquals(30, response.getBody().retryAfterSeconds());
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
    void 매장_조회_예외는_404_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleStoreProfileNotFound(
                new StoreProfileNotFoundException()
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("매장 정보를 찾을 수 없습니다.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 변경할_매장_정보가_없는_예외는_400_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleEmptyStoreProfileUpdate(
                new EmptyStoreProfileUpdateException()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("변경할 매장 정보가 없습니다.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 매장_정보_수정_업무_검증_예외는_400_fieldErrors_응답으로_변환한다() {
        ResponseEntity<ApiResponse<FieldErrors>> response = exceptionHandler.handleInvalidStoreProfileUpdateRequest(
                new InvalidStoreProfileUpdateRequestException(List.of(
                        new FieldError("businessHours", "요일별 영업시간을 확인해 주세요.")
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("입력값을 확인해 주세요.", response.getBody().message());
        assertEquals("businessHours", response.getBody().data().fieldErrors().getFirst().field());
    }

    @Test
    void 매장_정보_수정_사업자_인증_불일치는_422_fieldErrors_응답으로_변환한다() {
        ResponseEntity<ApiResponse<FieldErrors>> response = exceptionHandler.handleInvalidStoreBusinessVerification(
                new InvalidStoreBusinessVerificationException()
        );

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("입력값을 확인해 주세요.", response.getBody().message());
        assertEquals("businessVerificationId", response.getBody().data().fieldErrors().getFirst().field());
    }

    @Test
    void 매장_정보_수정_사업자_인증_만료는_410_응답으로_변환한다() {
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleBusinessVerificationExpired(
                new BusinessVerificationExpiredException()
        );

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertEquals("사업자 인증 결과가 만료되었습니다.", response.getBody().message());
        assertNull(response.getBody().data());
    }

    @Test
    void 매장_정보_수정_사업자번호_중복은_409_fieldErrors_응답으로_변환한다() {
        ResponseEntity<ApiResponse<FieldErrors>> response = exceptionHandler.handleDuplicateBusinessRegistrationNumber(
                new DuplicateBusinessRegistrationNumberException()
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("이미 등록된 사업자등록번호입니다.", response.getBody().message());
        assertEquals("businessRegNumber", response.getBody().data().fieldErrors().getFirst().field());
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
