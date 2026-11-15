package com.manager.app.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// filtros da lista de contas a receber (todos opcionais)
public record ContaReceberFiltro(LocalDate vencimentoDe, LocalDate vencimentoAte, LocalDate emissaoDe, LocalDate emissaoAte,
                                 LocalDate recebimentoDe, LocalDate recebimentoAte, List<SituacaoExibicaoContaReceber> situacao,
                                 String busca, UUID clienteId, Long categoriaId, Long centroCustoId, Long formaPrevistaId, Long bancoId,
                                 BigDecimal valorMin, BigDecimal valorMax) {

    public boolean temBusca() {
        return busca != null && !busca.isBlank();
    }

    public boolean temSituacao() {
        return situacao != null && !situacao.isEmpty();
    }

    public boolean temFiltroDeData() {
        return vencimentoDe != null || vencimentoAte != null || emissaoDe != null || emissaoAte != null
                || recebimentoDe != null || recebimentoAte != null;
    }

    // recebimento e banco dependem dos recebimentos (fase 3)
    public boolean temFiltroDeRecebimento() {
        return recebimentoDe != null || recebimentoAte != null || bancoId != null;
    }
}
