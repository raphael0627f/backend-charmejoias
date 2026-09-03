package com.manager.app.infra.exception;

public class ContoraApiException extends RuntimeException {
    public ContoraApiException(String message) {
        super(message);
    }
}
