package com.manager.app.dto.pedido;

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
    private UUID joiaId;
}
