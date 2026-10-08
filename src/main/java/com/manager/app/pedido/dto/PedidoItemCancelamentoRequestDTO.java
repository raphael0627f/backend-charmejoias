package com.manager.app.pedido.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PedidoItemCancelamentoRequestDTO {

    @NotNull(message = "Quantidade é obrigatória")
    @Positive(message = "Quantidade deve ser maior que zero")
    private Integer quantidade;

    @NotBlank(message = "Motivo é obrigatório")
    @jakarta.validation.constraints.Size(min = 5, message = "Motivo deve ter ao menos 5 caracteres")
    private String motivo;
}
