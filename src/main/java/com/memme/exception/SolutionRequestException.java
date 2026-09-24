package com.memme.exception;

public class SolutionRequestException extends RuntimeException {

    private final Reason reason;

    public SolutionRequestException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        STORE_OWNER_REQUIRED("본인 매장의 솔루션만 조회할 수 있습니다."),
        STORE_NOT_FOUND("매장 정보를 찾을 수 없습니다."),
        BUNDLE_NOT_FOUND("솔루션을 찾을 수 없습니다."),
        BUNDLE_EXPIRED("오늘의 솔루션이 만료되었습니다."),
        SAVE_TARGET_NOT_FOUND("저장할 솔루션을 찾을 수 없습니다."),
        SAVE_EXPIRED("저장할 수 있는 시간이 만료되었습니다."),
        SAVED_SOLUTION_NOT_FOUND("삭제되었거나 존재하지 않는 저장 솔루션입니다."),
        SAVED_SOLUTION_OWNER_REQUIRED("본인이 저장한 솔루션만 삭제할 수 있습니다."),
        INVALID_PAGE_SIZE("size는 1 이상 20 이하여야 합니다."),
        INVALID_SAVED_IDS("삭제할 savedIds를 한 개 이상 입력해 주세요.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}
