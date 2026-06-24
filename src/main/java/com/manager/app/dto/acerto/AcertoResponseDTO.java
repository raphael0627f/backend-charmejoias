package com.manager.app.dto.acerto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AcertoResponseDTO {

    private Long id;
    private BigDecimal valorConsignacoes;
    private BigDecimal valorMaleta;
    private BigDecimal valorAcessorios;
    private List<AcertoConsignacaoResponseDTO> consignacoes = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal ValorPagarConsignacaoMaleta;
    private BigDecimal ValorPagarAcessorios;

}
