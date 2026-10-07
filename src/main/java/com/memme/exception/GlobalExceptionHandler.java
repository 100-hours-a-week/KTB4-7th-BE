package com.memme.exception;

import com.memme.dto.auth.PasswordResetEmailRequest;
import com.memme.dto.auth.PasswordResetRateLimitErrorResponse;
import com.memme.dto.auth.LoginRateLimitErrorResponse;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.common.FieldError;
import com.memme.dto.common.FieldErrors;
import com.memme.dto.sales.StoreCostItemRequest;
import com.memme.dto.sales.StoreCostItemValidationErrors;
import com.memme.dto.sales.StoreCostItemValidationErrors.StoreCostItemFieldError;
import com.memme.dto.solution.SolutionNavigationErrorResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.exception.auth.DuplicateSignupException;
import com.memme.exception.auth.InvalidCurrentPasswordException;
import com.memme.exception.auth.InvalidLoginException;
import com.memme.exception.auth.LoginRateLimitExceededException;
import com.memme.exception.auth.InvalidPasswordChangeRequestException;
import com.memme.exception.auth.InvalidPasswordResetRequestException;
import com.memme.exception.auth.InvalidSignupRequestException;
import com.memme.exception.auth.PasswordResetTokenExpiredException;
import com.memme.exception.auth.PasswordResetRateLimitExceededException;
import com.memme.exception.auth.SignupCompletionExpiredException;
import com.memme.exception.chat.ChatInsufficientDataException;
import com.memme.exception.chat.ChatRequestException;
import com.memme.dto.chat.ChatMissingDataResponse;
import com.memme.dto.common.StatusResponse;
import com.memme.exception.noti.NotificationNotFoundException;
import com.memme.exception.noti.InvalidNotificationCursorException;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.exception.sales.InvalidStoreCostMonthException;
import com.memme.exception.sales.InternalSalesDataNotFoundException;
import com.memme.exception.solution.SolutionRequestException;
import com.memme.exception.store.AddressSearchFailedException;
import com.memme.exception.store.BusinessVerificationExpiredException;
import com.memme.exception.store.DuplicateBusinessRegistrationNumberException;
import com.memme.exception.store.BusinessStatusNotEligibleException;
import com.memme.exception.store.BusinessVerificationFailedException;
import com.memme.exception.store.EmptyStoreProfileUpdateException;
import com.memme.exception.store.InvalidStoreBusinessVerificationException;
import com.memme.exception.store.InvalidStoreProfileUpdateRequestException;
import com.memme.exception.store.StoreProfileNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST_FORMAT_MESSAGE = "요청 형식이 올바르지 않습니다.";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE =
            "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ChatInsufficientDataException.class)
    public ResponseEntity<StatusResponse<ChatMissingDataResponse>> handleChatInsufficientData(
            ChatInsufficientDataException exception
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new StatusResponse<>(
                        exception.getMessage(),
                        "INSUFFICIENT_DATA",
                        new ChatMissingDataResponse(List.of("INSUFFICIENT_HISTORY"))
                ));
    }

    @ExceptionHandler(ChatRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleChatRequest(ChatRequestException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case GENERATION_IN_PROGRESS -> HttpStatus.CONFLICT;
            case STORE_OWNER_REQUIRED -> HttpStatus.FORBIDDEN;
            case INVALID_CONTENT, RETRY_TARGET_NOT_FOUND, SOLUTION_NOT_READY ->
                    HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        return badRequestResponse();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return badRequestResponse();
    }

    @ExceptionHandler(InvalidStoreCostMonthException.class)
    public ResponseEntity<ApiResponse<StoreCostItemValidationErrors>> handleInvalidStoreCostMonth(
            InvalidStoreCostMonthException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse<>("입력값을 확인해 주세요.",
                new StoreCostItemValidationErrors(List.of(
                        new StoreCostItemFieldError("costMonth", "INVALID_FORMAT", exception.getMessage())))));
    }

    @ExceptionHandler(SalesAnalysisRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleSalesAnalysisRequest(
            SalesAnalysisRequestException exception
    ) {
        HttpStatus status = exception.getReason() == SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED
                ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(InternalSalesDataNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleInternalSalesDataNotFound(
            InternalSalesDataNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(SolutionRequestException.class)
    public ResponseEntity<?> handleSolutionRequest(
            SolutionRequestException exception
    ) {
        HttpStatus status = switch (exception.getReason()) {
            case STORE_OWNER_REQUIRED, SAVED_SOLUTION_OWNER_REQUIRED -> HttpStatus.FORBIDDEN;
            case BUNDLE_EXPIRED, SAVE_EXPIRED -> HttpStatus.GONE;
            case INVALID_PAGE_SIZE, INVALID_SAVED_IDS -> HttpStatus.BAD_REQUEST;
            case STORE_NOT_FOUND, BUNDLE_NOT_FOUND, SAVE_TARGET_NOT_FOUND,
                    SAVED_SOLUTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        if (exception.getReason() == SolutionRequestException.Reason.BUNDLE_EXPIRED) {
            return ResponseEntity.status(status).body(new SolutionNavigationErrorResponse(
                    exception.getMessage(),
                    "SOL-01-01",
                    null
            ));
        }
        if (exception.getReason() == SolutionRequestException.Reason.SAVED_SOLUTION_NOT_FOUND) {
            return ResponseEntity.status(status).body(new SolutionNavigationErrorResponse(
                    exception.getMessage(),
                    "SOL-03",
                    null
            ));
        }
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingSession(AuthenticationRequiredException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiResponse<>("로그인이 필요합니다.", null));
    }

    @ExceptionHandler(StoreProfileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStoreProfileNotFound(StoreProfileNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotificationNotFound(NotificationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(InvalidNotificationCursorException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidNotificationCursor(
            InvalidNotificationCursorException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(EmptyStoreProfileUpdateException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmptyStoreProfileUpdate(
            EmptyStoreProfileUpdateException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(InvalidStoreProfileUpdateRequestException.class)
    public ResponseEntity<ApiResponse<FieldErrors>> handleInvalidStoreProfileUpdateRequest(
            InvalidStoreProfileUpdateRequestException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(exception.getMessage(), new FieldErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(InvalidStoreBusinessVerificationException.class)
    public ResponseEntity<ApiResponse<FieldErrors>> handleInvalidStoreBusinessVerification(
            InvalidStoreBusinessVerificationException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiResponse<>(exception.getMessage(), new FieldErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(BusinessVerificationExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessVerificationExpired(
            BusinessVerificationExpiredException exception
    ) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(DuplicateBusinessRegistrationNumberException.class)
    public ResponseEntity<ApiResponse<FieldErrors>> handleDuplicateBusinessRegistrationNumber(
            DuplicateBusinessRegistrationNumberException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(exception.getMessage(), new FieldErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidLogin(InvalidLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    public ResponseEntity<LoginRateLimitErrorResponse> handleLoginRateLimitExceeded(
            LoginRateLimitExceededException exception
    ) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new LoginRateLimitErrorResponse(
                        exception.getMessage(), null, exception.getRetryAfterSeconds()
                ));
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCurrentPassword(
            InvalidCurrentPasswordException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(DuplicateSignupException.class)
    public ResponseEntity<ApiResponse<FieldErrors>> handleDuplicateSignup(DuplicateSignupException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(exception.getMessage(), new FieldErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(InvalidSignupRequestException.class)
    public ResponseEntity<ApiResponse<FieldErrors>> handleInvalidSignupRequest(InvalidSignupRequestException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiResponse<>(exception.getMessage(), new FieldErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(InvalidPasswordResetRequestException.class)
    public ResponseEntity<ApiResponse<FieldErrors>> handleInvalidPasswordResetRequest(
            InvalidPasswordResetRequestException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(exception.getMessage(), new FieldErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(InvalidPasswordChangeRequestException.class)
    public ResponseEntity<ApiResponse<FieldErrors>> handleInvalidPasswordChangeRequest(
            InvalidPasswordChangeRequestException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(exception.getMessage(), new FieldErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(SignupCompletionExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleSignupCompletionExpired(
            SignupCompletionExpiredException exception
    ) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(PasswordResetTokenExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordResetTokenExpired(
            PasswordResetTokenExpiredException exception
    ) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(PasswordResetRateLimitExceededException.class)
    public ResponseEntity<PasswordResetRateLimitErrorResponse> handlePasswordResetRateLimitExceeded(
            PasswordResetRateLimitExceededException exception
    ) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new PasswordResetRateLimitErrorResponse(
                        exception.getMessage(), null, exception.getRetryAfterSeconds()
                ));
    }

    @ExceptionHandler(BusinessStatusNotEligibleException.class)
    public ResponseEntity<ApiResponse<FieldErrors>> handleBusinessStatusNotEligible(
            BusinessStatusNotEligibleException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiResponse<>(exception.getMessage(), new FieldErrors(exception.getFieldErrors())));
    }

    @ExceptionHandler(BusinessVerificationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessVerificationFailed(
            BusinessVerificationFailedException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(AddressSearchFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAddressSearchFailed(AddressSearchFailedException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        if (exception.getBindingResult().getTarget() instanceof StoreCostItemRequest) {
            List<StoreCostItemFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                    .map(error -> new StoreCostItemFieldError(error.getField(),
                            switch (error.getCode() == null ? "" : error.getCode()) {
                                case "NotNull" -> "REQUIRED";
                                case "DecimalMin", "DecimalMax" -> "OUT_OF_RANGE";
                                default -> "INVALID_FORMAT";
                            }, error.getDefaultMessage()))
                    .toList();
            return ResponseEntity.badRequest().body(new ApiResponse<>("입력값을 확인해 주세요.",
                    new StoreCostItemValidationErrors(errors)));
        }
        if (exception.getBindingResult().getFieldErrors().stream().anyMatch(
                fieldError -> fieldError.isBindingFailure()
        )) {
            return badRequestResponse();
        }

        if (exception.getBindingResult().getTarget() instanceof PasswordResetEmailRequest) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>("이메일 형식이 올바르지 않습니다.", null));
        }

        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>("입력값을 확인해 주세요.", new FieldErrors(fieldErrors)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(Exception exception) {
        log.error("Unhandled server exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(INTERNAL_SERVER_ERROR_MESSAGE, null));
    }

    private ResponseEntity<ApiResponse<Void>> badRequestResponse() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(INVALID_REQUEST_FORMAT_MESSAGE, null));
    }

}
