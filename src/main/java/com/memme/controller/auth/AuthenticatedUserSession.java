package com.memme.controller.auth;

import java.io.Serializable;
import java.util.Objects;

public record AuthenticatedUserSession(
        Long userId,
        Long storeId
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "authenticatedUser";

    public AuthenticatedUserSession {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(storeId, "storeId");
    }
}
