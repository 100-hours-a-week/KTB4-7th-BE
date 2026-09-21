package com.memme.dto.auth;

public record SignupBusinessResponse(
        User user,
        Store store,
        String next
) {

    public record User(Long id, String email) {
    }

    public record Store(Long id, String storeName) {
    }
}
