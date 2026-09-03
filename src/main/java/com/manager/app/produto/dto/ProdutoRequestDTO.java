package com.manager.app.produto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.manager.app.categoria.Categoria;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProdutoRequestDTO {


    private String id;

    @NotBlank(message = "Código é obrigatório")
    private String codigo;

    @NotBlank(message = "Referência é obrigatório")
    private String referencia;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    private BigDecimal precoCusto;

    @NotNull(message = "Preço Venda é obrigatório")
    private BigDecimal precoVenda;

    @NotNull(message = "Ativo é obrigatório")
    private boolean ativo;

    private String observacoes;

    private String ncm;
    private Integer origemProduto;
    private String unidadeComercial;
    private String codigoSituacaoTributariaIcms;
    private String codigoSituacaoTributariaPis;
    private String codigoSituacaoTributariaCofins;

    @NotNull(message = "Categoria é obrigatória")
    private Long categoriaId;

    private Long fornecedorId;
    private List<Long> gruposIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
