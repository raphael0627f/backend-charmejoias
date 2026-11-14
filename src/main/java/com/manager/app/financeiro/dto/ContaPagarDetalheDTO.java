package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.SituacaoContaPagar;
import com.manager.app.financeiro.SituacaoExibicaoContaPagar;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ContaPagarDetalheDTO(Long id, Long numero, String descricao, String favorecido, ContaPagarResumoDTO.CategoriaRef categoria,
                                   ContaPagarResumoDTO.Ref centroCusto, ContaPagarResumoDTO.Ref formaPrevista, BigDecimal valor,
                                   BigDecimal valorPago, BigDecimal valorAberto, LocalDate emissao, String competencia, LocalDate vencimento,
                                   String documento, String linhaDigitavel, String observacao, SituacaoContaPagar situacao,
                                   SituacaoExibicaoContaPagar situacaoExibicao, UUID grupoParcelamentoId, Integer parcelaNumero,
                                   Integer parcelaTotal, Long recorrenciaId, Cancelamento cancelamento, List<Baixa> baixas,
                                   List<ContaPagarResumoDTO> parcelas, List<Evento> eventos, LocalDateTime createdAt, LocalDateTime updatedAt,
                                   UsuarioResumoDTO createdBy, UsuarioResumoDTO updatedBy, RecorrenciaResumo recorrencia, List<AnexoDTO> anexos) {

    // posicao = ordem do título na série; totalGerado = ocorrências já geradas
    public record RecorrenciaResumo(Long id, com.manager.app.financeiro.PeriodicidadeRecorrencia periodicidade, boolean ativa,
                                    LocalDate dataFim, long posicao, long totalGerado) {
    }

    public record Cancelamento(String motivo, LocalDateTime em, UsuarioResumoDTO por) {
    }

    public record Evento(Long id, String evento, String detalhe, UsuarioResumoDTO usuario, LocalDateTime dataHora) {
    }

    public record Baixa(Long id, LocalDate dataPagamento, BigDecimal valorPrincipal, BigDecimal juros, BigDecimal multa, BigDecimal desconto,
                        BigDecimal valorTotal, ContaPagarResumoDTO.Ref banco, ContaPagarResumoDTO.Ref formaPagamento, String observacao,
                        boolean estornada, String estornoMotivo, LocalDateTime estornadaEm, UsuarioResumoDTO estornadaPor,
                        LocalDateTime createdAt, UsuarioResumoDTO createdBy, Long movimentacaoId) {
    }
}
