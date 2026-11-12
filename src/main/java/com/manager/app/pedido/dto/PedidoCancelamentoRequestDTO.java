package com.manager.app.pedido.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PedidoCancelamentoRequestDTO {

    @NotBlank(message = "O campo Motivo é obrigatório.")
    @jakarta.validation.constraints.Size(min = 5, message = "O motivo deve ter ao menos 5 caracteres.")
    private String motivo;
}
