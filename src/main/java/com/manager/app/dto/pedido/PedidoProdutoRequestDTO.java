package com.manager.app.dto.pedido;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.manager.app.entity.Pedido;
import com.manager.app.entity.Produto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PedidoProdutoRequestDTO {

    private String descricao;
    private String variacao;
    private Integer quantidade;
    private BigDecimal preco;
    private UUID produtoId;
}
