package com.memme.dto.auth;

public record UserProfileResponse(
        User user
) {

    public record User(Long id, String email, String phone, String storeName) {
    }
}
