package com.manager.app.infinitepay;

// situação gravada do link de pagamento; "expirado" não existe aqui: é calculado pela validade
public enum StatusLinkPagamento {
    ATIVO, PAGO_MANUAL, PAGO_AUTOMATICO, ENCERRADO_TITULO_RECEBIDO, TITULO_CANCELADO, NAO_UTILIZAR
}
