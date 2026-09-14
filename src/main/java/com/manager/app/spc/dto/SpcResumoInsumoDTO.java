package com.manager.app.spc.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SpcResumoInsumoDTO {
    private Integer quantidadeTotal;
    private BigDecimal valorTotal;
    private BigDecimal valorUltimaOcorrencia;
    private String dataPrimeiraOcorrencia;
    private String dataUltimaOcorrencia;
    private String mesanoPrimeiraOcorrencia;
    private String mesanoUltimaOcorrencia;
}
