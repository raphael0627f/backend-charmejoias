package com.manager.app.pedido.dto;

import com.manager.app.pedido.CategoriaPedido;
import com.manager.app.pedido.StatusPedido;

import java.util.Map;

public record PedidoContagemDTO(Map<StatusPedido, Long> porStatus, Map<CategoriaPedido, Long> porCategoria) {
}
