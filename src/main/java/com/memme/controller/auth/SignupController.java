package com.memme.controller.auth;
import com.memme.dto.auth.*;
import com.memme.dto.common.ApiResponse;
import com.memme.service.auth.SignupService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/auth/signup") public class SignupController { private final SignupService service; public SignupController(SignupService service){this.service=service;} @PostMapping("/account") public ResponseEntity<ApiResponse<SignupAccountResponse>> signupAccount(@RequestBody SignupAccountRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>("가입 정보가 임시 저장되었습니다.",service.signupAccount(request)));}}
