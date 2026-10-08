package com.manager.app.pedido.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PedidoCancelamentoRequestDTO {

    @NotBlank(message = "Motivo é obrigatório")
    @jakarta.validation.constraints.Size(min = 5, message = "Motivo deve ter ao menos 5 caracteres")
    private String motivo;
}
