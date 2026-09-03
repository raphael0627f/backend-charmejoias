package com.manager.app.produto.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.categoria.Categoria;
import com.manager.app.fornecedor.Fornecedor;
import com.manager.app.grupo.Grupo;
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
        "codigo",
        "referencia",
        "nome",
        "precoCusto",
        "precoVenda",
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
        "createdAt",
        "updatedAt"
})
public class ProdutoResponseDTO {

    private UUID id;
    private String codigo;
    private String referencia;
    private String nome;
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private boolean ativo;
    private String observacoes;
    private String ncm;
    private Integer origemProduto;
    private String unidadeComercial;
    private String codigoSituacaoTributariaIcms;
    private String codigoSituacaoTributariaPis;
    private String codigoSituacaoTributariaCofins;
    private Categoria categoria;
    private Fornecedor fornecedor;
    private List<Grupo> grupos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
