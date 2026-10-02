package com.manager.app.rota.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// versao enxuta da consignacao pra exibir dentro de um item de Rota (deixar/trazer) - so o
// suficiente pra identificar a consignacao na tela, sem os produtos completos, mais a
// confirmacao de que essa consignacao especifica foi realmente deixada/trazida
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RotaConsignacaoResumoDTO {
    private Long id;
    private String descricao;
    private Integer quantidadeTotal;
    private BigDecimal valorTotal;
    private Boolean confirmada;
}
