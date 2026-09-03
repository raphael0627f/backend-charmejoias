package com.manager.app.notafiscal.dto;

import com.manager.app.produto.Produto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalItemResponseDTO {
    private Long id;
    private Produto produto;
    private BigDecimal quantidade;
    private BigDecimal valorUnitario;
    private BigDecimal valorTotal;
    private BigDecimal desconto;
    private String cfop;
}
