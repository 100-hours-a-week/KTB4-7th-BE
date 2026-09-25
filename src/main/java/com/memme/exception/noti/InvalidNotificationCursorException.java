package com.memme.exception.noti;

public class InvalidNotificationCursorException extends RuntimeException {

    public InvalidNotificationCursorException() {
        super("알림 목록 커서가 올바르지 않습니다.");
    }
}
