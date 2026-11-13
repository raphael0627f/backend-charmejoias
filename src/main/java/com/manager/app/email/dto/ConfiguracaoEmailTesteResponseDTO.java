package com.manager.app.email.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoEmailTesteResponseDTO {
    private boolean sucesso;
    private String mensagem;
}
