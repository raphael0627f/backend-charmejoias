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

    @NotBlank(message = "SKU é obrigatório")
    private String sku;

    @NotNull(message = "Preço de custo é obrigatório")
    private BigDecimal precoCusto;

    @NotNull(message = "Preço de venda é obrigatório")
    private BigDecimal precoVenda;

    @NotNull(message = "Quantidade em estoque é obrigatória")
    private Integer quantidadeEstoque;

    private boolean ativo = true;

    @NotEmpty(message = "Ao menos um item de variação é obrigatório")
    private List<Long> itensVariacaoIds;
}
