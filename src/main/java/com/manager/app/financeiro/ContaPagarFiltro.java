package com.manager.app.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// filtros da lista de contas a pagar (todos opcionais)
public record ContaPagarFiltro(LocalDate vencimentoDe, LocalDate vencimentoAte, LocalDate emissaoDe, LocalDate emissaoAte,
                               LocalDate pagamentoDe, LocalDate pagamentoAte, List<SituacaoExibicaoContaPagar> situacao,
                               String busca, Long categoriaId, Long centroCustoId, Long formaPrevistaId, Long bancoId,
                               BigDecimal valorMin, BigDecimal valorMax, Boolean estimado) {

    public boolean temBusca() {
        return busca != null && !busca.isBlank();
    }

    public boolean temSituacao() {
        return situacao != null && !situacao.isEmpty();
    }

    public boolean temFiltroDeData() {
        return vencimentoDe != null || vencimentoAte != null || emissaoDe != null || emissaoAte != null
                || pagamentoDe != null || pagamentoAte != null;
    }
}
