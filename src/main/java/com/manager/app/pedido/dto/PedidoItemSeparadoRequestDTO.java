package com.manager.app.pedido.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PedidoItemSeparadoRequestDTO {

    @NotNull(message = "Informe se o item foi separado")
    private Boolean separado;
}
