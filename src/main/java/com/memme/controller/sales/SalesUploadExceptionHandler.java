package com.memme.controller.sales;

import com.memme.dto.common.FailureResponse;
import com.memme.exception.sales.SalesUploadProcessingException;
import com.memme.exception.sales.SalesUploadQueryException;
import com.memme.exception.sales.SalesUploadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = SalesUploadController.class)
public class SalesUploadExceptionHandler {

    @ExceptionHandler(SalesUploadRequestException.class)
    public ResponseEntity<FailureResponse> handleRequest(SalesUploadRequestException exception) {
        return ResponseEntity.status(statusOf(exception.getFailReason())).body(new FailureResponse(
                exception.getMessage(),
                exception.getFailReason(),
                null
        ));
    }

    @ExceptionHandler(SalesUploadProcessingException.class)
    public ResponseEntity<FailureResponse> handleProcessing(SalesUploadProcessingException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new FailureResponse(
                exception.getMessage(),
                "UPLOAD_PROCESSING_ERROR",
                null
        ));
    }

    @ExceptionHandler(SalesUploadQueryException.class)
    public ResponseEntity<FailureResponse> handleQuery(SalesUploadQueryException exception) {
        return ResponseEntity.status(statusOf(exception.getReason())).body(new FailureResponse(
                exception.getMessage(),
                exception.getReason().name(),
                null
        ));
    }

    private HttpStatus statusOf(String failReason) {
        return switch (failReason) {
            case "STORE_OWNER_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "FILE_TOO_LARGE" -> HttpStatus.CONTENT_TOO_LARGE;
            case "UNSUPPORTED_FILE_FORMAT" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case "EMPTY_FILE" -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private HttpStatus statusOf(SalesUploadQueryException.Reason reason) {
        return switch (reason) {
            case INVALID_PAGE -> HttpStatus.BAD_REQUEST;
            case STORE_OWNER_REQUIRED -> HttpStatus.FORBIDDEN;
            case UPLOAD_NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
    }
}
