package com.manager.app.usuario;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// bean isolado nesta parte porque o Spring Security completo (com toda a configuração de
// SecurityFilterChain) só entra numa parte seguinte - até lá só precisamos do BCrypt pra
// nunca gravar a senha do Usuario em texto puro
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}
