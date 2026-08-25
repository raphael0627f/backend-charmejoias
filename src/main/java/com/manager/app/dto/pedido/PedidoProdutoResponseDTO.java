package com.manager.app.dto.pedido;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "descricao",
        "variacao",
        "dataEntrega",
        "quantidade",
        "preco",
        "produtoId",
        "produtoCodigo",
        "produtoNome",
        "joiaId",
        "joiaCodigo",
        "joiaNome",
})
public class PedidoProdutoResponseDTO {

    private String descricao;
    private String variacao;
    private Integer quantidade;
    private BigDecimal preco;
    private UUID produtoId;
    private String produtoCodigo;
    private String produtoNome;
    private UUID joiaId;
    private String joiaCodigo;
    private String joiaNome;
}
