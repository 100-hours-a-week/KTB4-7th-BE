package com.memme.exception;

public class SalesAnalysisRequestException extends RuntimeException {
    public enum Reason { INVALID_PERIOD, STORE_OWNER_REQUIRED }

    private final Reason reason;

    public SalesAnalysisRequestException(Reason reason) {
        super(reason == Reason.INVALID_PERIOD
                ? "조회 기간이 올바르지 않습니다."
                : "매장 소유자만 매출 분석을 조회할 수 있습니다.");
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
