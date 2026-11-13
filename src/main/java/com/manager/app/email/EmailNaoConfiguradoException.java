package com.manager.app.email;

// servidor SMTP nao configurado (MAIL_HOST vazio): 503 com mensagem clara
public class EmailNaoConfiguradoException extends RuntimeException {
    public EmailNaoConfiguradoException() {
        super("Envio de e-mail não configurado. Informe ao administrador do sistema.");
    }
}
