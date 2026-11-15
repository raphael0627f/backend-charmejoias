package com.manager.app.financeiro;

// situação mostrada ao usuário: a gravada mais VENCIDO (aberto ou parcial com vencimento anterior a hoje)
public enum SituacaoExibicaoContaReceber {
    ABERTO, RECEBIDO_PARCIAL, RECEBIDO, CANCELADO, VENCIDO
}
