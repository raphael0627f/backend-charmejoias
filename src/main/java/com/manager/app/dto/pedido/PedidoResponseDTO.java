package com.manager.app.dto.pedido;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.infra.restEnum.StatusPedido;
import com.manager.app.infra.restEnum.TipoItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "observacao",
        "dataEntrega",
        "status",
        "tipo",
        "clienteId",
        "clienteCodigo",
        "clienteNome",
        "produtosResponse",
})
public class PedidoResponseDTO {

    private Long id;
    private String observacao;
    private LocalDate dataEntrega;
    private StatusPedido status;
    private TipoItem tipo;
    private UUID clienteId;
    private String clienteCodigo;
    private String clienteNome;
    private List<PedidoProdutoResponseDTO> produtosResponse = new ArrayList<>();
}
