package com.manager.app.email.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// as regras (obrigatorios quando ativo, porta 1-65535, e-mail valido, starttls x ssl) sao conferidas no service, com mensagens proprias.
// senha vazia ou omitida MANTEM a senha ja salva.
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoEmailRequestDTO {
    private String host;
    private Integer porta;
    private String usuario;
    private String senha;
    private String remetente;
    private String nomeRemetente;
    private Boolean starttls;
    private Boolean ssl;
    private Integer timeoutMs;
    private Boolean ativo;
}
