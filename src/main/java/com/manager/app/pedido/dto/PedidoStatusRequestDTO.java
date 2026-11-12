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

    @NotNull(message = "O campo Id é obrigatório.")
    private Long id;

    @NotNull(message = "O campo Status é obrigatório.")
    private StatusPedido status;
}
