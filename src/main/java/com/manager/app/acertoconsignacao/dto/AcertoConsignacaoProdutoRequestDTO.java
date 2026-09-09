package com.manager.app.acertoconsignacao.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class AcertoConsignacaoProdutoRequestDTO {

    private UUID produtoId;
    private Long produtoVariacaoId;
    private UUID joiaId;

    @NotNull(message = "Quantidade é obrigatória")
    private Integer quantidade;
}
