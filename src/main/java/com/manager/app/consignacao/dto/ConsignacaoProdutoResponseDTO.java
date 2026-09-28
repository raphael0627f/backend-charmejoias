package com.manager.app.consignacao.dto;

import com.manager.app.joia.dto.JoiaResponseDTO;
import com.manager.app.produto.dto.ProdutoResponseDTO;
import com.manager.app.produto.dto.ProdutoVariacaoResponseDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ConsignacaoProdutoResponseDTO {
    private Long id;
    private Integer quantidade;
    private BigDecimal preco;
    private ProdutoResponseDTO produto;
    private ProdutoVariacaoResponseDTO produtoVariacao;
    private JoiaResponseDTO joia;
}
