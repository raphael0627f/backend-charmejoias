package com.manager.app.acertoconsignacao.dto;

import com.manager.app.joia.dto.JoiaResponseDTO;
import com.manager.app.produto.dto.ProdutoResponseDTO;
import com.manager.app.produto.dto.ProdutoVariacaoResponseDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AcertoConsignacaoProdutoResponseDTO {
    private Long id;
    private Integer quantidade;
    private ProdutoResponseDTO produto;
    private ProdutoVariacaoResponseDTO produtoVariacao;
    private JoiaResponseDTO joia;
}
