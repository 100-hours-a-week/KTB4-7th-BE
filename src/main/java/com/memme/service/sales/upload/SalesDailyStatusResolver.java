package com.memme.service.sales.upload;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.memme.entity.sales.SalesDailyStatus;
import org.springframework.stereotype.Component;

@Component
public class SalesDailyStatusResolver {

    public Boolean confirmedClosedStatus(
            LocalDate salesDate,
            LocalDateTime scheduleUpdatedAt,
            Boolean configuredClosed
    ) {
        if (scheduleUpdatedAt == null || configuredClosed == null
                || !salesDate.isAfter(scheduleUpdatedAt.toLocalDate())) {
            return null;
        }
        return configuredClosed;
    }

    public SalesDailyStatus resolve(
            boolean hasOrderRows,
            boolean zeroSalesConfirmed,
            Boolean storeClosed
    ) {
        if (hasOrderRows) {
            return SalesDailyStatus.COMPLETE;
        }
        if (Boolean.TRUE.equals(storeClosed)) {
            return SalesDailyStatus.CLOSED;
        }
        if (zeroSalesConfirmed) {
            return SalesDailyStatus.COMPLETE;
        }
        if (Boolean.FALSE.equals(storeClosed)) {
            return SalesDailyStatus.MISSING;
        }
        return SalesDailyStatus.UNKNOWN;
    }
}
