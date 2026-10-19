package com.manager.app.montagem.dto;

import com.manager.app.pedido.CategoriaPedido;
import com.manager.app.pedido.StatusPedido;
import com.manager.app.shared.TipoItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class MontagemPedidoResumoDTO {
    private Long id;
    private StatusPedido status;
    private CategoriaPedido categoria;
    private TipoItem tipo;
    private LocalDate dataPedido;
    private LocalDate dataEntrega;
    private String observacao;
}
