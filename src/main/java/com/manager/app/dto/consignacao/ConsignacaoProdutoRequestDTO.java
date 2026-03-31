package com.manager.app.dto.consignacao;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ConsignacaoProdutoRequestDTO {

    private UUID produtoId;
    private Integer quantidade;
    private BigDecimal preco;
}
