package com.manager.app.consignacao.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuantidadePorCategoriaDTO {
    private String categoria;
    private Integer quantidade;
}
