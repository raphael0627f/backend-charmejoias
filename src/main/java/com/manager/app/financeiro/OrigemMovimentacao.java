package com.manager.app.financeiro;

public enum OrigemMovimentacao {
    SALDO_INICIAL, PAGAMENTO_CONTA_PAGAR, ESTORNO_PAGAMENTO, TRANSFERENCIA, AJUSTE, ESTORNO_MANUAL,
    // entrada gerada pelo recebimento de título a receber e a saída que o reverte no estorno (Fase 3 do Contas a Receber)
    RECEBIMENTO_CONTA_RECEBER, ESTORNO_RECEBIMENTO
}
