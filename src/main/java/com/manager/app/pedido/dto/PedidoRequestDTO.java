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

    // obrigatória só quando o pedido tem cliente (pedido sem cliente pode ficar sem data de entrega)
    private LocalDate dataEntrega;

    @NotNull(message = "O campo Data do pedido é obrigatório.")
    private LocalDate dataPedido;

    @NotNull(message = "O campo Categoria é obrigatório.")
    private CategoriaPedido categoria;

    @NotNull(message = "O campo Tipo é obrigatório.")
    private TipoItem tipo;

    // opcional: pedido sem cliente (avulso), como a consignação
    private UUID clienteId;

    // opcional
    private Long mostruarioId;

    @Valid
    @NotEmpty(message = "Informe ao menos um produto.")
    private List<PedidoProdutoRequestDTO> produtosRequest = new ArrayList<>();

}
