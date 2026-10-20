package com.manager.app.pedido.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// produto cadastrado a que um item avulso é vinculado durante a separação
@Getter
@Setter
@NoArgsConstructor
public class PedidoItemProdutoRequestDTO {

    @NotNull(message = "Informe o produto")
    private UUID produtoId;

    // obrigatório quando o produto tem variações
    private Long produtoVariacaoId;

    // quantas unidades do item vão para esse produto; vazio = todas as disponíveis. Um item com
    // várias unidades pode ser dividido entre produtos diferentes
    @Min(value = 1, message = "A quantidade deve ser maior que zero")
    private Integer quantidade;
}
