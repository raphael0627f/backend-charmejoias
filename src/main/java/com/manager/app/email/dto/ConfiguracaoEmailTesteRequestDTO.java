package com.manager.app.email.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConfiguracaoEmailTesteRequestDTO {

    @NotBlank(message = "O campo Destinatário do teste é obrigatório.")
    private String para;

    // opcional: dados ainda nao salvos (senha vazia = usa a salva). Omitido = testa a configuracao salva.
    @Valid
    private ConfiguracaoEmailRequestDTO configuracao;
}
