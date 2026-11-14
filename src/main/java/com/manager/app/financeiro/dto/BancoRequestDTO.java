package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.TipoBanco;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// saldoInicial pode ser negativo; dataSaldoInicial assume a data de hoje quando omitida (na criação)
public record BancoRequestDTO(
        @NotBlank(message = "O nome é obrigatório.") @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.") String nome,
        @NotNull(message = "O tipo é obrigatório.") TipoBanco tipo,
        @Size(max = 10, message = "O código do banco deve ter no máximo 10 caracteres.") String codigoBanco,
        @Size(max = 20, message = "A agência deve ter no máximo 20 caracteres.") String agencia,
        @Size(max = 30, message = "A conta deve ter no máximo 30 caracteres.") String conta,
        BigDecimal saldoInicial,
        LocalDate dataSaldoInicial,
        @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres.") String observacao) {
}
