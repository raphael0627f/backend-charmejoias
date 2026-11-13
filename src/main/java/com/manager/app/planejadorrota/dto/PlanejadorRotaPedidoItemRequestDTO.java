package com.manager.app.planejadorrota.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PlanejadorRotaPedidoItemRequestDTO {

    @NotNull(message = "O campo Pedido é obrigatório.")
    private Long pedidoId;

    // null/false = ainda nao confirmado (mesmo padrao das consignacoes a deixar)
    private Boolean confirmada;
}
