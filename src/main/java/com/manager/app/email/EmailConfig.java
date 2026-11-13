package com.manager.app.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

// JavaMailSender da conta SMTP fixa da empresa. So existe se MAIL_HOST estiver configurado: sem ele nenhum bean e criado
// e a aplicacao sobe normalmente (o envio responde "Envio de e-mail não configurado", ver EmailService).
@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnExpression("!'${app.mail.host:}'.trim().isEmpty()")
    public JavaMailSender javaMailSender(
            @Value("${app.mail.host}") String host,
            @Value("${app.mail.port:587}") int port,
            @Value("${app.mail.username:}") String username,
            @Value("${app.mail.password:}") String password,
            @Value("${app.mail.starttls:true}") boolean starttls,
            @Value("${app.mail.ssl:false}") boolean ssl,
            @Value("${app.mail.timeout-ms:30000}") int timeoutMs) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        boolean autenticar = username != null && !username.isBlank();
        if (autenticar) {
            sender.setUsername(username);
            sender.setPassword(password);
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
}
