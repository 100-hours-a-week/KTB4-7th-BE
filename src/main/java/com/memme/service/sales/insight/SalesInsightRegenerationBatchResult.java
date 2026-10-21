package com.memme.service.sales.insight;

public record SalesInsightRegenerationBatchResult(
        long candidates,
        long eligible,
        long completed,
        long skipped,
        long failed
)
{
}
