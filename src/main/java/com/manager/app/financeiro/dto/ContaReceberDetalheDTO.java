package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.financeiro.SituacaoExibicaoContaReceber;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// anexos seguem vazios até a fase 4 (o frontend já pode contar com o campo)
public record ContaReceberDetalheDTO(Long id, Long numero, String descricao, ContaReceberResumoDTO.ClienteRef cliente,
                                     ContaReceberResumoDTO.CategoriaRef categoria, ContaReceberResumoDTO.Ref centroCusto,
                                     ContaReceberResumoDTO.Ref formaPrevista, BigDecimal valor, BigDecimal valorRecebido, BigDecimal valorAberto,
                                     LocalDate emissao, String competencia, LocalDate vencimento, String documento, String observacao,
                                     SituacaoContaReceber situacao, SituacaoExibicaoContaReceber situacaoExibicao, UUID grupoParcelamentoId,
                                     Integer parcelaNumero, Integer parcelaTotal, Cancelamento cancelamento, List<Recebimento> recebimentos,
                                     List<ContaReceberResumoDTO> parcelas, List<Evento> eventos, LocalDateTime createdAt, LocalDateTime updatedAt,
                                     UsuarioResumoDTO createdBy, UsuarioResumoDTO updatedBy, List<Object> anexos) {

    public record Cancelamento(String motivo, LocalDateTime em, UsuarioResumoDTO por) {
    }

    // situacao: ATIVO ou ESTORNADO
    public record Recebimento(Long id, LocalDate dataRecebimento, BigDecimal valorPrincipal, BigDecimal juros, BigDecimal multa, BigDecimal desconto,
                              BigDecimal valorTotal, ContaReceberResumoDTO.Ref banco, ContaReceberResumoDTO.Ref formaPagamento, String observacao,
                              String situacao, boolean estornado, String estornoMotivo, LocalDateTime estornadoEm, UsuarioResumoDTO estornadoPor,
                              LocalDateTime createdAt, UsuarioResumoDTO createdBy, Long movimentacaoId) {
    }

    public record Evento(Long id, String evento, String detalhe, UsuarioResumoDTO usuario, LocalDateTime dataHora) {
    }
}
