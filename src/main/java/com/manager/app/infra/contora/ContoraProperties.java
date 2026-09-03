package com.manager.app.infra.contora;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "contora")
@Getter
@Setter
public class ContoraProperties {

    // o ambiente precisa bater com o ambiente da chave (uma chave de homologação
    // só cria/opera empresas de homologação, e vice-versa)
    private String apiKey;
    private String baseUrl;
    private String defaultEnvironment;
}
