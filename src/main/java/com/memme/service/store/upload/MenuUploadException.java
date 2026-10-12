package com.memme.service.store.upload;

import org.springframework.http.HttpStatus;

public class MenuUploadException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Integer fileIndex;

    public MenuUploadException(HttpStatus status, String code, Integer fileIndex, String message) {
        super(message);
        this.status = status;
        this.code = code;
        this.fileIndex = fileIndex;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Integer getFileIndex() { return fileIndex; }
}
