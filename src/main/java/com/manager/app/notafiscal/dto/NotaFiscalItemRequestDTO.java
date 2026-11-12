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

    @NotNull(message = "O campo Produto é obrigatório.")
    private UUID produtoId;

    @NotNull(message = "O campo Quantidade é obrigatório.")
    private BigDecimal quantidade;

    @NotNull(message = "O campo Valor unitário é obrigatório.")
    private BigDecimal valorUnitario;

    private BigDecimal desconto;

    @NotBlank(message = "O campo CFOP é obrigatório.")
    private String cfop;
}
