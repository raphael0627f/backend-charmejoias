package com.manager.app.pedido.dto;

import com.manager.app.pedido.CategoriaPedido;
import com.manager.app.shared.TipoItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.manager.app.produto.Produto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PedidoRequestDTO {

    private Long id;
    private String observacao;

    @NotNull(message = "Data de entrega é obrigatória")
    private LocalDate dataEntrega;

    @NotNull(message = "Data do pedido é obrigatória")
    private LocalDate dataPedido;

    @NotNull(message = "Categoria é obrigatória")
    private CategoriaPedido categoria;

    @NotNull(message = "Tipo é obrigatório")
    private TipoItem tipo;

    @NotNull(message = "clienteId é obrigatório")
    private UUID clienteId;

    @Valid
    @NotEmpty(message = "Informe ao menos um produto")
    private List<PedidoProdutoRequestDTO> produtosRequest = new ArrayList<>();

}
