package com.memme.exception;
import com.memme.dto.common.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class GlobalExceptionHandler {
 @ExceptionHandler(DuplicateSignupException.class) public ResponseEntity<ApiResponse<Void>> handleDuplicateSignup(DuplicateSignupException e){return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(e.getMessage(),null));}
 @ExceptionHandler(InvalidSignupRequestException.class) public ResponseEntity<ApiResponse<Void>> handleInvalidSignupRequest(InvalidSignupRequestException e){return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ApiResponse<>(e.getMessage(),null));}
}
