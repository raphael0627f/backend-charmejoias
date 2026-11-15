package com.manager.app.financeiro;

// situação gravada do título a receber; VENCIDO não existe aqui: é calculado (SituacaoExibicaoContaReceber)
public enum SituacaoContaReceber {
    ABERTO, RECEBIDO_PARCIAL, RECEBIDO, CANCELADO
}
