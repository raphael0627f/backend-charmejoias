package com.manager.app.shared.exception;

// consultas pesadas simultâneas em excesso (HTTP 503)
public class SistemaOcupadoException extends RuntimeException {
    public SistemaOcupadoException(String message) {
        super(message);
    }
}
