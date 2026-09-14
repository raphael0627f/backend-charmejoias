package com.manager.app.spc.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spc")
@Getter
@Setter
public class SpcProperties {

    private String consultaUrlHomologacao;
    private String consultaUrlProducao;
    private String insumoUrlHomologacao;
    private String insumoUrlProducao;
    private String operador;
    private String senha;
    private String defaultEnvironment;
}
