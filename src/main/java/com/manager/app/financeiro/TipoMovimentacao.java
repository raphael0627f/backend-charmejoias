package com.manager.app.financeiro;

public enum TipoMovimentacao {
    ENTRADA, SAIDA;

    public TipoMovimentacao inverso() {
        return this == ENTRADA ? SAIDA : ENTRADA;
    }
}
