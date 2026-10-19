package com.manager.app.consignacao;

import java.util.UUID;

// produto (e variação) a acrescentar numa consignação existente, com a quantidade a somar
public record ConsignacaoItemAdicionar(UUID produtoId, Long produtoVariacaoId, int quantidade) {
}
