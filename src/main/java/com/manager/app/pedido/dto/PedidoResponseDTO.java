package com.manager.app.pedido.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.pedido.CategoriaPedido;
import com.manager.app.pedido.StatusPedido;
import com.manager.app.shared.TipoItem;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "observacao",
        "dataPedido",
        "dataEntrega",
        "status",
        "categoria",
        "tipo",
        "impresso",
        "impressoEm",
        "clienteId",
        "clienteCodigo",
        "clienteNome",
        "cidade",
        "mostruarioId",
        "mostruarioDescricao",
        "produtosResponse",
        "motivoCancelamento",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy",
})
public class PedidoResponseDTO {

    private Long id;
    private String observacao;
    private LocalDate dataPedido;
    private LocalDate dataEntrega;
    private StatusPedido status;
    private CategoriaPedido categoria;
    private TipoItem tipo;
    private boolean impresso;
    private java.time.LocalDateTime impressoEm;
    private String motivoCancelamento;
    private UUID clienteId;
    private String clienteCodigo;
    private String clienteNome;
    private String cidade;
    private Long mostruarioId;
    private String mostruarioDescricao;
    private List<PedidoProdutoResponseDTO> produtosResponse = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
