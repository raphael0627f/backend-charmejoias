package com.manager.app.dto.acerto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.infra.restEnum.TipoConsignacaoAcerto;
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
