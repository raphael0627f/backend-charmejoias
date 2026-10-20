package com.manager.app.montagem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MontagemInstrucaoMarcacaoRequestDTO {

    @NotNull(message = "Informe se a instrução foi feita")
    private Boolean concluida;
}
