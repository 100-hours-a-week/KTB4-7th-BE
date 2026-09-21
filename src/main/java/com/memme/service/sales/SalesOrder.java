package com.memme.service.sales;

import java.util.List;
import java.util.Objects;

public record SalesOrder(
    SalesOrderKey key,
    boolean valid,
    List<SalesOrderItem> items
) {

    public SalesOrder {
        Objects.requireNonNull(key, "key");
        items = List.copyOf(items);
    }
}
