package com.manager.app.planejadorrota.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.manager.app.pedido.StatusPedido;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

// versao enxuta do pedido pra identificar na tela: dentro da rota (pedidosDeixar, com a confirmacao) e na lista
// de pedidos que podem ser deixados (sem "confirmada"). O status e o atual do pedido, nao o da gravacao.
@Getter
@Setter
@NoArgsConstructor
public class PlanejadorRotaPedidoResumoDTO {
    private Long id;
    private StatusPedido status;
    private Integer itensCount;
    private Integer quantidadeTotal;
    private BigDecimal valorTotal;
    private String observacao;
    private LocalDate dataEntrega;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean confirmada;
}
