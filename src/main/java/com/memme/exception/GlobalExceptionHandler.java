package com.memme.exception;

import com.memme.dto.auth.PasswordResetEmailRequest;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.common.FieldError;
import com.memme.dto.common.FieldErrors;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST_FORMAT_MESSAGE = "요청 형식이 올바르지 않습니다.";

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

    @ExceptionHandler(SalesAnalysisRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleSalesAnalysisRequest(
            SalesAnalysisRequestException exception
    ) {
        HttpStatus status = exception.getReason() == SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED
                ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiResponse<>(exception.getMessage(), null));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingSession(AuthenticationRequiredException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>("로그인이 필요합니다.", null));
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidLogin(InvalidLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(exception.getMessage(), null));
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

    private ResponseEntity<ApiResponse<Void>> badRequestResponse() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(INVALID_REQUEST_FORMAT_MESSAGE, null));
    }

}
