package com.memme.exception.sales;

public class SalesUploadCostEntryException extends RuntimeException {
    public enum Reason { UPLOAD_NOT_FOUND, UPLOAD_NOT_COMPLETED, INVALID_MONTHS }

    private final Reason reason;

    public SalesUploadCostEntryException(Reason reason) {
        super(switch (reason) {
            case UPLOAD_NOT_FOUND -> "매출 업로드를 찾을 수 없습니다.";
            case UPLOAD_NOT_COMPLETED -> "매출 파일 처리가 완료되지 않았습니다.";
            case INVALID_MONTHS -> "파일에 포함된 매출 월과 입력한 비용 월이 일치하지 않습니다.";
        });
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
