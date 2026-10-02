package com.manager.app.pedido.dto;

import com.manager.app.pedido.StatusPedido;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PedidoStatusLoteRequestDTO {

    @NotEmpty(message = "Informe ao menos um pedido")
    private List<Long> ids;

    @NotNull(message = "Status é obrigatório")
    private StatusPedido status;
}
