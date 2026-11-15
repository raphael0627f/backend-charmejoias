package com.manager.app.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.manager.app.financeiro.OrigemMovimentacao;
import com.manager.app.financeiro.TipoMovimentacao;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// saldoApos só vem no extrato com um banco selecionado (nos demais casos o campo é omitido)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovimentacaoResponseDTO(Long id, Long bancoId, String bancoNome, LocalDate data, TipoMovimentacao tipo, BigDecimal valor,
                                      OrigemMovimentacao origem, String descricao, Long transferenciaId, boolean estornada,
                                      Long estornoDeId, String motivoEstorno, LocalDateTime estornadaEm,
                                      UsuarioResumoDTO estornadaPor, UsuarioResumoDTO usuario, LocalDateTime criadoEm,
                                      BigDecimal saldoApos, Long tituloId, Long tituloNumero, Long pagamentoId) {
}
