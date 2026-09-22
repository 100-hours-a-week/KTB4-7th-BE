package com.memme.dto.auth;

public record LoginResponse(
        User user
) {

    public record User(Long id, String email) {
    }
}
