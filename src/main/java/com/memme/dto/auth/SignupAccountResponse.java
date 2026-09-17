package com.memme.dto.auth;
import java.time.OffsetDateTime;
public record SignupAccountResponse(String signupToken, OffsetDateTime expiresAt) {}
