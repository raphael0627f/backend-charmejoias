package com.manager.app.acertojoia.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.acertojoia.TipoConsignacaoAcertoJoia;
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
public class AcertoJoiaConsignacaoResponseDTO {

    private String descricao;
    private Integer quantidadeProdutos;
    private BigDecimal valorTotal;
    private TipoConsignacaoAcertoJoia tipo;
    private Long consignacaoId;
}
