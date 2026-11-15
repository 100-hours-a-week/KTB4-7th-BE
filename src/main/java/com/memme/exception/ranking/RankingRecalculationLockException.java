package com.memme.exception.ranking;

public class RankingRecalculationLockException extends RuntimeException {

    private final Reason reason;

    public RankingRecalculationLockException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RankingRecalculationLockException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        ACQUISITION_TIMEOUT,
        ACQUISITION_INTERRUPTED,
        REDIS_UNAVAILABLE,
        OWNERSHIP_LOST,
        RELEASE_FAILED
    }
}
