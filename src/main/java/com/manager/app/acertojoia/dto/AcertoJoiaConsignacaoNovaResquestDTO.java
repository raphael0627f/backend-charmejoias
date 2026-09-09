package com.manager.app.acertojoia.dto;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class AcertoJoiaConsignacaoNovaResquestDTO {

    private String descricao;
    private Integer quantidadeProdutos;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorTotal;
}
