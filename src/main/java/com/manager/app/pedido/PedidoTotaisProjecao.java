package com.manager.app.pedido;

import java.math.BigDecimal;

public interface PedidoTotaisProjecao {
    Long getPedidoId();
    Long getItensCount();
    Integer getQuantidadeTotal();
    BigDecimal getValorTotal();
}
