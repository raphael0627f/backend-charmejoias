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
        "id",
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
        "grupoId",
        "grupoNome",
        "quantidadeCancelada",
        "motivoCancelamento",
        "separado",
})
public class PedidoProdutoResponseDTO {

    private Long id;
    private String descricao;
    private String variacao;
    private Integer quantidade;
    private BigDecimal preco;
    private Integer quantidadeCancelada;
    private String motivoCancelamento;
    private boolean separado;
    private UUID produtoId;
    private String produtoCodigo;
    private String produtoNome;
    private Long produtoVariacaoId;
    private String produtoVariacaoSku;
    private List<ItemVariacaoDTO> produtoVariacaoItens;
    private UUID joiaId;
    private String joiaCodigo;
    private String joiaNome;
    // grupoId só vem preenchido em itens avulsos (onde o usuário escolheu o
    // departamento manualmente); grupoNome vem sempre que dá pra resolver - do(s)
    // grupo(s) do produto/joia cadastrado (juntos, separados por "/") ou, se
    // avulso, do grupo escolhido
    private Long grupoId;
    private String grupoNome;
}
