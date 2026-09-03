package com.manager.app.notafiscal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalPagamentoRequestDTO {

    @NotBlank(message = "Forma de pagamento é obrigatória")
    private String formaPagamento;

    @NotNull(message = "Valor é obrigatório")
    private BigDecimal valor;
}
