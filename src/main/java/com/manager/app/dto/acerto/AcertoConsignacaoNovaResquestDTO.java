package com.manager.app.dto.acerto;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class AcertoConsignacaoNovaResquestDTO {

    private String descricao;
    private Integer quantidadeProdutos;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorTotal;
}
