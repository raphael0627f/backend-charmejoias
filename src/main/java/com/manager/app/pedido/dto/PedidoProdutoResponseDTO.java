package com.manager.app.pedido.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.variacao.dto.ItemVariacaoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "descricao",
        "variacao",
        "dataEntrega",
        "quantidade",
        "preco",
        "produtoId",
        "produtoCodigo",
        "produtoNome",
        "produtoVariacaoId",
        "produtoVariacaoSku",
        "produtoVariacaoItens",
        "joiaId",
        "joiaCodigo",
        "joiaNome",
})
public class PedidoProdutoResponseDTO {

    private String descricao;
    private String variacao;
    private Integer quantidade;
    private BigDecimal preco;
    private UUID produtoId;
    private String produtoCodigo;
    private String produtoNome;
    private Long produtoVariacaoId;
    private String produtoVariacaoSku;
    private List<ItemVariacaoDTO> produtoVariacaoItens;
    private UUID joiaId;
    private String joiaCodigo;
    private String joiaNome;
}
