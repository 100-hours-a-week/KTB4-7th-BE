package com.memme.service.sales;

import java.time.LocalDateTime;
import java.util.Objects;

public record SalesOrderKey(
    SalesChannel channel,
    String posOrderNo,
    LocalDateTime orderedAt
) {

    public SalesOrderKey {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(posOrderNo, "posOrderNo");
        Objects.requireNonNull(orderedAt, "orderedAt");
    }
}
