package com.manager.app.email;

import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

// dados de SMTP em uso num envio (vindos do banco, do corpo do teste ou das variaveis MAIL_*). A senha nunca e logada:
// o toString nao a inclui.
public record EmailConfigEfetiva(String host, int porta, String usuario, String senha, String remetente, String nomeRemetente,
                                 boolean starttls, boolean ssl, int timeoutMs) {

    public JavaMailSenderImpl criarSender(){
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(porta);
        sender.setDefaultEncoding("UTF-8");
        boolean autenticar = usuario != null && !usuario.isBlank();
        if (autenticar) {
            sender.setUsername(usuario);
            sender.setPassword(senha);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(autenticar));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.timeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.writetimeout", String.valueOf(timeoutMs));
        return sender;
    }

    @Override
    public String toString() {
        return "EmailConfigEfetiva[host=" + host + ", porta=" + porta + ", usuario=" + usuario + ", remetente=" + remetente + "]";
    }
}
