package com.memme.service.chat;

public record ChatAiEvent(
        Type type,
        String content,
        String evidenceJson,
        String errorCode,
        String errorMessage
) {
    public static ChatAiEvent chunk(String content, String evidenceJson) {
        return new ChatAiEvent(Type.CHUNK, content, evidenceJson, null, null);
    }

    public static ChatAiEvent error(String code, String message) {
        return new ChatAiEvent(Type.ERROR, null, null, code, message);
    }

    public enum Type {
        CHUNK,
        ERROR,
        DONE
    }
}
