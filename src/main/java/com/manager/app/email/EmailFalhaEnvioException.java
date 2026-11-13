package com.manager.app.email;

// falha do servidor SMTP (conexao, autenticacao, recusa): 502 sem detalhes tecnicos nem credenciais na mensagem
public class EmailFalhaEnvioException extends RuntimeException {
    public EmailFalhaEnvioException(String message, Throwable cause) {
        super(message, cause);
    }
}
