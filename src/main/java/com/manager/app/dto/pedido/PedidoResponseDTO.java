package com.manager.app.dto.pedido;

import com.manager.app.infra.restEnum.StatusPedido;
import jakarta.validation.constraints.NotNull;
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
public class PedidoResponseDTO {

    private Long id;
    private String observacao;
    private LocalDate dataEntrega;
    private StatusPedido status;
    private UUID clienteId;
    private String clienteCodigo;
    private String clienteNome;
    private List<PedidoProdutoResponseDTO> produtosRequest = new ArrayList<>();
}
