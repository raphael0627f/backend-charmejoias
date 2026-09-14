package com.manager.app.etiqueta.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EtiquetaDTO {
    private String descricao;
    private String codigoBarras;
    private BigDecimal precoVenda;
    private String sku;
}
