package com.manager.app.produto.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.variacao.dto.ItemVariacaoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "sku", "precoCusto", "precoVenda", "quantidadeEstoque", "ativo", "codigoBarras", "itensVariacao", "createdAt", "updatedAt" })
public class ProdutoVariacaoResponseDTO {
    private Long id;
    private String sku;
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private Integer quantidadeEstoque;
    private boolean ativo;
    private String codigoBarras;
    private List<ItemVariacaoDTO> itensVariacao;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
