package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.financeiro.SituacaoExibicaoContaReceber;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContaReceberResumoDTO(Long id, Long numero, String descricao, ClienteRef cliente, String pagadorNome, String pagadorDocumento,
                                    String pagadorExibicao, String tipoPagador, Origem origem, CategoriaRef categoria, Ref centroCusto,
                                    Ref formaPrevista, BigDecimal valor, BigDecimal valorRecebido, BigDecimal valorAberto, LocalDate emissao,
                                    String competencia, LocalDate vencimento, String documento, SituacaoContaReceber situacao,
                                    SituacaoExibicaoContaReceber situacaoExibicao, UUID grupoParcelamentoId, Integer parcelaNumero,
                                    Integer parcelaTotal, String motivoCancelamento, LocalDateTime dataCancelamento, int quantidadeAnexos) {

    public record Ref(Long id, String nome) {
    }

    // origem do título gerado a partir de consignação: tipo PRE_ACERTO ou ACERTO
    public record Origem(String tipo, Long documentoId, Long consignacaoId) {
    }

    public record ClienteRef(UUID id, String nome, String documento) {
    }

    // grupoId/grupoNome nulos quando a categoria escolhida é um grupo
    public record CategoriaRef(Long id, String nome, Long grupoId, String grupoNome) {
    }
}
