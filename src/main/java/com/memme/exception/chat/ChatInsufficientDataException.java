package com.memme.exception.chat;

public class ChatInsufficientDataException extends RuntimeException {

    public ChatInsufficientDataException() {
        super("예측에 필요한 매출 이력이 부족해 챗봇 답변을 생성할 수 없습니다.");
    }
}
