package com.manager.app.produto.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.categoria.Categoria;
import com.manager.app.fornecedor.Fornecedor;
import com.manager.app.grupo.Grupo;
import com.manager.app.ncm.Ncm;
import com.manager.app.produto.TipoProduto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "sku",
        "referencia",
        "nome",
        "tipo",
        "imagemUrl",
        "precoCusto",
        "precoVenda",
        "quantidadeEstoque",
        "ativo",
        "observacoes",
        "ncm",
        "origemProduto",
        "unidadeComercial",
        "codigoSituacaoTributariaIcms",
        "codigoSituacaoTributariaPis",
        "codigoSituacaoTributariaCofins",
        "categoria",
        "fornecedor",
        "grupos",
        "variacoes",
        "createdAt",
        "updatedAt"
})
public class ProdutoResponseDTO {

    private UUID id;
    private String sku;
    private String referencia;
    private String nome;
    private TipoProduto tipo;
    private String imagemUrl;
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private Integer quantidadeEstoque;
    private boolean ativo;
    private String observacoes;
    private Ncm ncm;
    private Integer origemProduto;
    private String unidadeComercial;
    private String codigoSituacaoTributariaIcms;
    private String codigoSituacaoTributariaPis;
    private String codigoSituacaoTributariaCofins;
    private Categoria categoria;
    private Fornecedor fornecedor;
    private List<Grupo> grupos;
    private List<ProdutoVariacaoResponseDTO> variacoes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
