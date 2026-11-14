package com.manager.app.shared.exception;

// arquivo acima do limite permitido (HTTP 413)
public class ArquivoMuitoGrandeException extends RuntimeException {
    public ArquivoMuitoGrandeException(String message) {
        super(message);
    }
}
