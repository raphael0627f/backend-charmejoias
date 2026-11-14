package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.SituacaoContaPagar;
import com.manager.app.financeiro.SituacaoExibicaoContaPagar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContaPagarResumoDTO(Long id, Long numero, String descricao, String favorecido, CategoriaRef categoria, Ref centroCusto,
                                  Ref formaPrevista, BigDecimal valor, BigDecimal valorPago, BigDecimal valorAberto, LocalDate emissao,
                                  String competencia, LocalDate vencimento, String documento, SituacaoContaPagar situacao,
                                  SituacaoExibicaoContaPagar situacaoExibicao, UUID grupoParcelamentoId, Integer parcelaNumero,
                                  Integer parcelaTotal, String motivoCancelamento, LocalDateTime dataCancelamento) {

    public record Ref(Long id, String nome) {
    }

    // grupoId/grupoNome nulos quando a categoria escolhida é um grupo
    public record CategoriaRef(Long id, String nome, Long grupoId, String grupoNome) {
    }
}
