package com.manager.app.preacerto.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PreAcertoConsignacaoProdutoRequestDTO {

    private UUID produtoId;
    private Long produtoVariacaoId;
    private UUID joiaId;

    @NotNull(message = "Quantidade é obrigatória")
    private Integer quantidade;

    private BigDecimal preco;
}
