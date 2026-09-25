package com.memme.exception.sales;

public class SalesAnalysisRetryException extends RuntimeException {

    public enum Reason {
        STORE_OWNER_REQUIRED,
        UPLOAD_NOT_FOUND,
        RETRY_NOT_ALLOWED,
        RETRY_IN_PROGRESS,
        RETRY_FAILED
    }

    private final Reason reason;
    private final String failReason;

    public SalesAnalysisRetryException(Reason reason, String failReason) {
        super(messageOf(reason));
        this.reason = reason;
        this.failReason = failReason;
    }

    public SalesAnalysisRetryException(Reason reason, String failReason, Throwable cause) {
        super(messageOf(reason), cause);
        this.reason = reason;
        this.failReason = failReason;
    }

    public Reason getReason() {
        return reason;
    }

    public String getFailReason() {
        return failReason;
    }

    private static String messageOf(Reason reason) {
        return switch (reason) {
            case STORE_OWNER_REQUIRED -> "매장 소유자만 매출 분석을 다시 실행할 수 있습니다.";
            case UPLOAD_NOT_FOUND -> "매출 업로드를 찾을 수 없습니다.";
            case RETRY_NOT_ALLOWED -> "이 업로드는 다시 분석할 수 없습니다. 새 파일을 업로드해 주세요.";
            case RETRY_IN_PROGRESS -> "매출 분석 재시도가 이미 진행 중입니다.";
            case RETRY_FAILED -> "매출 분석 재시도에 실패했습니다.";
        };
    }
}
