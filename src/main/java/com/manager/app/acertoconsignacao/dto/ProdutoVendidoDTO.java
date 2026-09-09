package com.manager.app.acertoconsignacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.joia.Joia;
import com.manager.app.produto.Produto;
import com.manager.app.produto.ProdutoVariacao;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({ "produto", "produtoVariacao", "joia", "quantidade", "preco", "valorTotal" })
public class ProdutoVendidoDTO {

    private Produto produto;
    private ProdutoVariacao produtoVariacao;
    private Joia joia;
    private Integer quantidade;
    private BigDecimal preco;
    private BigDecimal valorTotal;
}
