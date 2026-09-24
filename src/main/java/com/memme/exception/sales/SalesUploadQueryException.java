package com.memme.exception.sales;

public class SalesUploadQueryException extends RuntimeException {

    public enum Reason {
        INVALID_PAGE,
        STORE_OWNER_REQUIRED,
        UPLOAD_NOT_FOUND
    }

    private final Reason reason;

    public SalesUploadQueryException(Reason reason) {
        super(messageOf(reason));
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    private static String messageOf(Reason reason) {
        return switch (reason) {
            case INVALID_PAGE -> "페이지 요청 값을 확인해 주세요.";
            case STORE_OWNER_REQUIRED -> "본인 매장의 매출 업로드만 조회할 수 있습니다.";
            case UPLOAD_NOT_FOUND -> "매출 업로드를 찾을 수 없습니다.";
        };
    }
}
