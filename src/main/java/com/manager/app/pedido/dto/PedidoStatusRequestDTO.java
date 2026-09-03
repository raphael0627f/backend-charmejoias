package com.manager.app.pedido.dto;

import com.manager.app.pedido.StatusPedido;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PedidoStatusRequestDTO {

    @NotNull(message = "Id é obrigatório")
    private Long id;

    @NotNull(message = "Status é obrigatório")
    private StatusPedido status;
}
