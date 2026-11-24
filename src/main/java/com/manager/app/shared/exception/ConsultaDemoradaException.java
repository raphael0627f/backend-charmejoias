package com.manager.app.shared.exception;

// consulta pesada que excedeu o tempo limite (HTTP 504)
public class ConsultaDemoradaException extends RuntimeException {
    public ConsultaDemoradaException(String message) {
        super(message);
    }
}
