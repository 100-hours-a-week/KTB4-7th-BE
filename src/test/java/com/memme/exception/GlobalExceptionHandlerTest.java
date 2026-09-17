package com.memme.exception;
import static org.junit.jupiter.api.Assertions.*;
import com.memme.dto.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
class GlobalExceptionHandlerTest { private final GlobalExceptionHandler handler=new GlobalExceptionHandler();
 @Test void 중복은_409로_변환한다(){ResponseEntity<ApiResponse<Void>> response=handler.handleDuplicateSignup(new DuplicateSignupException());assertEquals(HttpStatus.CONFLICT,response.getStatusCode());assertNull(response.getBody().data());}
 @Test void 입력값_오류는_422로_변환한다(){ResponseEntity<ApiResponse<Void>> response=handler.handleInvalidSignupRequest(new InvalidSignupRequestException());assertEquals(HttpStatus.UNPROCESSABLE_CONTENT,response.getStatusCode());assertNull(response.getBody().data());}}
