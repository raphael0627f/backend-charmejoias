package com.manager.app.dto.pedido;

import com.manager.app.infra.restEnum.StatusPedido;
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
