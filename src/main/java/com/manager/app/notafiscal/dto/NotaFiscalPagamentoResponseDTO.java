package com.manager.app.notafiscal.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalPagamentoResponseDTO {
    private Long id;
    private String formaPagamento;
    private BigDecimal valor;
}
