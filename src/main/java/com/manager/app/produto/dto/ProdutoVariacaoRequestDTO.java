package com.manager.app.produto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ProdutoVariacaoRequestDTO {

    private Long id;

    @NotBlank(message = "O campo SKU é obrigatório.")
    private String sku;

    @NotNull(message = "O campo Preço de custo é obrigatório.")
    private BigDecimal precoCusto;

    @NotNull(message = "O campo Preço de venda é obrigatório.")
    private BigDecimal precoVenda;

    @NotNull(message = "O campo Quantidade em estoque é obrigatório.")
    private Integer quantidadeEstoque;

    private boolean ativo = true;

    @NotEmpty(message = "Informe ao menos um item de variação.")
    private List<Long> itensVariacaoIds;
}
