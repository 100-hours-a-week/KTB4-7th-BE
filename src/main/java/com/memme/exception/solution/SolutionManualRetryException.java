package com.memme.exception.solution;

public class SolutionManualRetryException extends RuntimeException {

    private final Reason reason;

    public SolutionManualRetryException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        STORE_OWNER_REQUIRED("본인 매장의 솔루션만 다시 생성할 수 있습니다."),
        NOT_ALLOWED("오늘 실패한 솔루션만 다시 생성할 수 있습니다."),
        DATA_NOT_READY("솔루션 생성에 필요한 데이터가 준비되지 않았습니다.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}
