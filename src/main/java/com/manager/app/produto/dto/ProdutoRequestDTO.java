package com.manager.app.produto.dto;

import com.manager.app.produto.TipoProduto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProdutoRequestDTO {


    private String id;

    @NotBlank(message = "O campo SKU é obrigatório.")
    private String sku;

    @NotBlank(message = "O campo Referência é obrigatório.")
    private String referencia;

    @NotBlank(message = "O campo Nome é obrigatório.")
    private String nome;

    @NotNull(message = "O campo Tipo é obrigatório.")
    private TipoProduto tipo;

    private BigDecimal precoCusto;

    private BigDecimal precoVenda;

    private Integer quantidadeEstoque;

    @NotNull(message = "O campo Ativo é obrigatório.")
    private boolean ativo;

    private String observacoes;

    private String ncmCodigo;
    private Integer origemProduto;
    private String unidadeComercial;
    private String codigoSituacaoTributariaIcms;
    private String codigoSituacaoTributariaPis;
    private String codigoSituacaoTributariaCofins;

    @NotNull(message = "O campo Categoria é obrigatório.")
    private Long categoriaId;

    private Long fornecedorId;
    private List<Long> gruposIds;

    @Valid
    private List<ProdutoVariacaoRequestDTO> variacoes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
