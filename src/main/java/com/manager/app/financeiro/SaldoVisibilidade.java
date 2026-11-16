package com.manager.app.financeiro;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

// Regra única de quem pode ver saldos de bancos (saldo atual, saldo inicial e valores de saldo em avisos).
// Quem possui permissão de movimentação ou de cadastro financeiro sempre vê. Perfis que possuem apenas
// permissões CONTA_RECEBER_* não veem. As permissões CONTA_PAGAR_* mantêm o comportamento anterior (veem),
// pois o Contas a Pagar não foi alterado; para ocultar também do Pagar basta remover esse conjunto.
@Component
public class SaldoVisibilidade {

    private static final Set<String> PERMISSOES_SALDO = Set.of(
            "MOVIMENTACAO_VISUALIZAR", "MOVIMENTACAO_GERENCIAR", "CADASTRO_FINANCEIRO_GERENCIAR");
    private static final Set<String> PERMISSOES_PAGAR_MANTIDAS = Set.of(
            "CONTA_PAGAR_VISUALIZAR", "CONTA_PAGAR_GERENCIAR", "CONTA_PAGAR_PAGAR");

    public boolean podeVerSaldo() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        // sem usuário autenticado (processos internos, testes) não há o que restringir
        if (autenticacao == null || !autenticacao.isAuthenticated()) return true;
        for (GrantedAuthority a : autenticacao.getAuthorities()) {
            String p = a.getAuthority();
            if (PERMISSOES_SALDO.contains(p) || PERMISSOES_PAGAR_MANTIDAS.contains(p)) return true;
        }
        return false;
    }
}
