package com.manager.app.financeiro.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferenciaRequestDTO(
        @NotNull(message = "O banco de origem é obrigatório.") Long bancoOrigemId,
        @NotNull(message = "O banco de destino é obrigatório.") Long bancoDestinoId,
        @NotNull(message = "O valor é obrigatório.") BigDecimal valor,
        @NotNull(message = "A data é obrigatória.") LocalDate data,
        @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres.") String descricao) {
}
