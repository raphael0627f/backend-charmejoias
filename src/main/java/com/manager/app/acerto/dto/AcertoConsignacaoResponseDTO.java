package com.manager.app.acerto.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.acerto.TipoConsignacaoAcerto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "descricao",
})
public class AcertoConsignacaoResponseDTO {

    private String descricao;
    private Integer quantidadeProdutos;
    private BigDecimal valorTotal;
    private TipoConsignacaoAcerto tipo;
    private Long consignacaoId;
}
