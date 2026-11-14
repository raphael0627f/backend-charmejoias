package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.TipoBanco;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BancoResponseDTO(Long id, String nome, TipoBanco tipo, String codigoBanco, String agencia, String conta,
                               BigDecimal saldoInicial, LocalDate dataSaldoInicial, String observacao, boolean ativo,
                               BigDecimal saldoAtual, LocalDateTime createdAt, LocalDateTime updatedAt,
                               UsuarioResumoDTO createdBy, UsuarioResumoDTO updatedBy) {
}
