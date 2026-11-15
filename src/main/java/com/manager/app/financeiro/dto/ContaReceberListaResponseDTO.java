package com.manager.app.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// totais valem para todo o filtro (não só para a página)
public record ContaReceberListaResponseDTO(List<ContaReceberResumoDTO> content, int page, int size, long totalElements, int totalPages,
                                           Periodo periodoVencimento, Totais totais) {

    public record Periodo(LocalDate de, LocalDate ate) {
    }

    public record Totais(long quantidade, BigDecimal valorTotal, BigDecimal valorRecebido, BigDecimal valorAberto, BigDecimal valorVencido) {
    }
}
