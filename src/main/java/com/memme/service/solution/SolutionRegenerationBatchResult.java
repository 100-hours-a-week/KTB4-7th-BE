package com.memme.service.solution;

public record SolutionRegenerationBatchResult(
        long candidates,
        long eligible,
        long completed,
        long skipped,
        long failed
)
{
}
