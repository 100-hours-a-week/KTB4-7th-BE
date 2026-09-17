package com.memme.dto.auth;

public record SignupAccountRequest(
        String email,
        String password,
        String passwordConfirm,
        String phone,
        Agreements agreements
) {

    public record Agreements(
            boolean termsOfService,
            String termsOfServiceVersion,
            boolean privacyPolicy,
            String privacyPolicyVersion
    ) {
    }
}
