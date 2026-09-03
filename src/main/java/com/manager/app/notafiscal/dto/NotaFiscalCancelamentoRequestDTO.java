package com.manager.app.notafiscal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalCancelamentoRequestDTO {

    @NotBlank(message = "Motivo do cancelamento é obrigatório")
    private String motivo;
}
