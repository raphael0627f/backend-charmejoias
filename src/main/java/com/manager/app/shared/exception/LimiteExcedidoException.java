package com.manager.app.shared.exception;

// excesso de chamadas (HTTP 429)
public class LimiteExcedidoException extends RuntimeException {
    public LimiteExcedidoException(String message) {
        super(message);
    }
}
