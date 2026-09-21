package com.memme.exception;

import com.memme.dto.common.ApiResponse;
import com.memme.dto.common.FieldError;
import com.memme.dto.common.FieldErrors;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
    public ResponseEntity<ApiResponse<FieldErrors>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiResponse<>("입력값을 확인해 주세요.", new FieldErrors(fieldErrors)));
    }
}
