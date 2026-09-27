package com.memme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.qa-account")
public record QaAccountProperties(
        boolean enabled,
        String email,
        String password,
        String phone,
        String businessRegistrationNo,
        String storeName,
        String postalCode,
        String address,
        String addressDetail
) {
}
