package com.manager.app.notafiscal;

public class DanfeApiException extends RuntimeException {
    public DanfeApiException(String message) {
        super(message);
    }

    public DanfeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
