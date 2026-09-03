package com.manager.app.notafiscal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.manager.app.produto.Produto;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalItemRequestDTO {

    @NotNull(message = "Produto é obrigatório")
    private UUID produtoId;

    @NotNull(message = "Quantidade é obrigatória")
    private BigDecimal quantidade;

    @NotNull(message = "Valor unitário é obrigatório")
    private BigDecimal valorUnitario;

    private BigDecimal desconto;

    @NotBlank(message = "CFOP é obrigatório")
    private String cfop;
}
