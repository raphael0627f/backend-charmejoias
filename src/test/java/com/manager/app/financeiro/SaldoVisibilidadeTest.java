package com.manager.app.financeiro;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaldoVisibilidadeTest {

    private final SaldoVisibilidade regra = new SaldoVisibilidade();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void logar(String... permissoes) {
        var autoridades = java.util.Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", "x", autoridades));
    }

    @Test
    void somenteContaReceberNaoVeSaldo() {
        logar("CONTA_RECEBER_VISUALIZAR", "CONTA_RECEBER_GERENCIAR", "CONTA_RECEBER_RECEBER", "CONTA_RECEBER_ESTORNAR");
        assertFalse(regra.podeVerSaldo());
    }

    @Test
    void movimentacaoECadastroFinanceiroVeemSaldo() {
        logar("MOVIMENTACAO_VISUALIZAR");
        assertTrue(regra.podeVerSaldo());
        logar("CADASTRO_FINANCEIRO_GERENCIAR");
        assertTrue(regra.podeVerSaldo());
    }

    @Test
    void contaPagarMantemComportamentoAnterior() {
        logar("CONTA_PAGAR_PAGAR");
        assertTrue(regra.podeVerSaldo());
    }
}
