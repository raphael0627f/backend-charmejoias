package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.TipoMovimentacao;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AjusteRequestDTO(
        @NotNull(message = "O banco é obrigatório.") Long bancoId,
        @NotNull(message = "O tipo (ENTRADA ou SAIDA) é obrigatório.") TipoMovimentacao tipo,
        @NotNull(message = "O valor é obrigatório.") BigDecimal valor,
        @NotNull(message = "A data é obrigatória.") LocalDate data,
        String motivo) {
}
