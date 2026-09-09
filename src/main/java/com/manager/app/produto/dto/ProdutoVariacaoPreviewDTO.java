package com.manager.app.produto.dto;

import com.manager.app.variacao.dto.ItemVariacaoDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoVariacaoPreviewDTO {
    private String sku;
    private List<ItemVariacaoDTO> itensVariacao;
}
