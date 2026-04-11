package com.manager.app.dto.acerto;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AcertoRequestDTO {

    private Long id;
    private BigDecimal valorConsignacoes;
    private BigDecimal valorMaleta;
    private BigDecimal valorAcessorios;

    private List<Long> consignacoesIds = new ArrayList<>();
    private List<Long> maletasIds = new ArrayList<>();
    private List<Long> acessoriosIds = new ArrayList<>();
    private List<AcertoConsignacaoNovaResquestDTO> consignacaoesNovas = new ArrayList<>();
}
