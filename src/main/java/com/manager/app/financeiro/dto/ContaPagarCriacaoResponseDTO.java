package com.manager.app.financeiro.dto;

import java.util.List;
import java.util.UUID;

// grupoParcelamentoId é nulo no título simples; recorrenciaId só existe quando há recorrência
public record ContaPagarCriacaoResponseDTO(UUID grupoParcelamentoId, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Long recorrenciaId,
                                           List<ContaPagarResumoDTO> titulos) {
}
