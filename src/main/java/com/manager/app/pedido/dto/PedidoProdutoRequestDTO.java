package com.manager.app.pedido.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PedidoProdutoRequestDTO {

    private String descricao;
    private String variacao;
    private Integer quantidade;
    private BigDecimal preco;
    private UUID produtoId;
    private Long produtoVariacaoId;
    private UUID joiaId;
}
