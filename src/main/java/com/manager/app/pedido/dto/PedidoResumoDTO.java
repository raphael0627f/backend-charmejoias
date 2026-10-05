package com.manager.app.pedido.dto;

import com.manager.app.pedido.CategoriaPedido;
import com.manager.app.pedido.StatusPedido;
import com.manager.app.shared.TipoItem;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PedidoResumoDTO {
    private Long id;
    private UUID clienteId;
    private String clienteCodigo;
    private String clienteNome;
    private String cidade;
    private Long mostruarioId;
    private String mostruarioDescricao;
    private CategoriaPedido categoria;
    private StatusPedido status;
    private TipoItem tipo;
    private boolean impresso;
    private LocalDateTime impressoEm;
    private LocalDate dataPedido;
    private LocalDate dataEntrega;
    private String observacao;
    private String motivoCancelamento;
    private int itensCount;
    private int quantidadeTotal;
    private BigDecimal valorTotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
