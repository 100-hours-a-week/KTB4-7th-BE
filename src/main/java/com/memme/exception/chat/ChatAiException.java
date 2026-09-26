package com.memme.exception.chat;

public class ChatAiException extends RuntimeException {

    public ChatAiException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChatAiException(String message) {
        super(message);
    }
}
