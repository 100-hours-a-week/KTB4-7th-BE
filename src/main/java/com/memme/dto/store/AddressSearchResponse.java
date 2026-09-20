package com.memme.dto.store;

import java.util.List;

public record AddressSearchResponse(
        List<Address> addresses,
        String nextCursor
) {

    public record Address(
            String postalCode,
            String roadAddress,
            String jibunAddress
    ) {
    }
}
