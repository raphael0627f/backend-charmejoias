package com.manager.app.infinitepay;

// MANUAL: a baixa é feita pelo usuário (recebimento com linkId). AUTOMATICO: a baixa vem do webhook confirmado (banco escolhido na geração do link).
public enum ModoLinkPagamento {
    MANUAL, AUTOMATICO
}
