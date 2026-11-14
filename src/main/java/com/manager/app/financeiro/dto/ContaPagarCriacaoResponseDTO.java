package com.manager.app.financeiro.dto;

import java.util.List;
import java.util.UUID;

// grupoParcelamentoId é nulo no título simples
public record ContaPagarCriacaoResponseDTO(UUID grupoParcelamentoId, List<ContaPagarResumoDTO> titulos) {
}
