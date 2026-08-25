package com.manager.app.infra.exception;

public class ItemInvalidoException extends RuntimeException {
    public ItemInvalidoException(String message) {
        super(message);
    }
}
