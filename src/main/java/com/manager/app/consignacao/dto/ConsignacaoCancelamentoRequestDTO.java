package com.manager.app.consignacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConsignacaoCancelamentoRequestDTO {

    @NotBlank(message = "O campo Motivo é obrigatório.")
    @Size(min = 5, message = "O motivo do cancelamento deve ter ao menos 5 caracteres.")
    private String motivo;
}
