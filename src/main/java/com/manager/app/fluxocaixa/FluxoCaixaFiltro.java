package com.manager.app.fluxocaixa;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// filtros do fluxo de caixa. Cada filtro de categoria vale só para o seu lado (receitas x despesas); centro de custo e cliente valem para os dois
// (o título a pagar do cliente é a devolução); favorecido vale só para contas a pagar e, informado, retira as contas a receber.
public record FluxoCaixaFiltro(LocalDate inicio, LocalDate fim, List<Long> bancoIds, List<Long> categoriaReceitaIds, List<Long> categoriaDespesaIds,
                               List<Long> centroCustoIds, UUID clienteId, String favorecido) {

    public FluxoCaixaFiltro {
        bancoIds = bancoIds == null ? List.of() : List.copyOf(bancoIds);
        categoriaReceitaIds = categoriaReceitaIds == null ? List.of() : List.copyOf(categoriaReceitaIds);
        categoriaDespesaIds = categoriaDespesaIds == null ? List.of() : List.copyOf(categoriaDespesaIds);
        centroCustoIds = centroCustoIds == null ? List.of() : List.copyOf(centroCustoIds);
        favorecido = favorecido == null || favorecido.isBlank() ? null : favorecido.trim();
    }

    public boolean incluiReceber() {
        return favorecido == null;
    }

    // saldos só fazem sentido sem filtros que recortam títulos (a movimentação bancária não tem categoria, centro de custo, cliente nem favorecido)
    public boolean temFiltroDeTitulo() {
        return !categoriaReceitaIds.isEmpty() || !categoriaDespesaIds.isEmpty() || !centroCustoIds.isEmpty() || clienteId != null || favorecido != null;
    }

    public FluxoCaixaFiltro comPeriodo(LocalDate novoInicio, LocalDate novoFim) {
        return new FluxoCaixaFiltro(novoInicio, novoFim, bancoIds, categoriaReceitaIds, categoriaDespesaIds, centroCustoIds, clienteId, favorecido);
    }
}
