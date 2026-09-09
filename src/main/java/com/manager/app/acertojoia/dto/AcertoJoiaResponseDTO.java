package com.manager.app.acertojoia.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AcertoJoiaResponseDTO {

    private Long id;
    private BigDecimal valorConsignacoes;
    private BigDecimal valorMaleta;
    private BigDecimal valorAcessorios;
    private List<AcertoJoiaConsignacaoResponseDTO> consignacoes = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal ValorPagarConsignacaoMaleta;
    private BigDecimal ValorPagarAcessorios;

}
