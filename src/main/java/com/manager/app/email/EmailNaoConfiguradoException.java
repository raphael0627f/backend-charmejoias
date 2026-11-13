package com.manager.app.email;

// envio de e-mail sem configuracao utilizavel (nada salvo/ativo no banco e sem variaveis MAIL_*): 503 com mensagem clara
public class EmailNaoConfiguradoException extends RuntimeException {
    public EmailNaoConfiguradoException() {
        super("Envio de e-mail não configurado. Solicite ao administrador que configure o e-mail em Sistema.");
    }

    public EmailNaoConfiguradoException(String message) {
        super(message);
    }
}
