package com.manager.app.email;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

// traduz a falha de SMTP em um motivo legivel em portugues, sem credenciais nem detalhes internos
public final class EmailErros {

    private EmailErros() {}

    public static String descrever(Throwable erro){
        for (Throwable t = erro; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof AuthenticationFailedException) {
                return "falha de autenticação: usuário ou senha incorretos (no Gmail, utilize uma senha de app).";
            }
            if (t instanceof UnknownHostException) {
                return "servidor não encontrado. Verifique o endereço do servidor SMTP.";
            }
            if (t instanceof SocketTimeoutException) {
                return "o servidor não respondeu a tempo. Verifique o servidor, a porta e as opções de segurança.";
            }
            if (t instanceof SSLException) {
                return "falha na conexão segura. Verifique as opções STARTTLS/SSL e a porta (587 com STARTTLS; 465 com SSL).";
            }
            if (t instanceof ConnectException) {
                return "não foi possível conectar ao servidor. Verifique o endereço, a porta e o firewall.";
            }
            if (t instanceof SendFailedException) {
                return "o servidor recusou o remetente ou o destinatário.";
            }
        }
        return "erro na comunicação com o servidor de e-mail.";
    }
}
