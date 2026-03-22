package com.manager.app.infra.exception;

public class ObjetoNaoEncontradoException extends RuntimeException {
    public ObjetoNaoEncontradoException(String message) {
        super(message);
    }
}
