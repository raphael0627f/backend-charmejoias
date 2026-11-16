package com.manager.app.infinitepay;

// falha na chamada à InfinitePay; a mensagem é formal e nunca contém a chave nem o corpo bruto da resposta (vira 502)
public class InfinitePayApiException extends RuntimeException {
    public InfinitePayApiException(String message) {
        super(message);
    }

    public InfinitePayApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
