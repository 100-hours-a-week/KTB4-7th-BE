package com.memme.exception.chat;

public class ChatRequestException extends RuntimeException {

    private final Reason reason;

    public ChatRequestException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_CONTENT("질문은 공백을 제외하고 1자 이상 300자 이하로 입력해 주세요."),
        GENERATION_IN_PROGRESS("이전 답변을 생성하고 있습니다."),
        RETRY_TARGET_NOT_FOUND("재시도할 질문을 찾을 수 없습니다."),
        SOLUTION_NOT_READY("오늘의 솔루션을 먼저 확인해 주세요."),
        STORE_OWNER_REQUIRED("매장 소유자만 챗봇을 이용할 수 있습니다.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }
}
