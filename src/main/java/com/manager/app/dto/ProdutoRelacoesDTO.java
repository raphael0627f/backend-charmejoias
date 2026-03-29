package com.manager.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoRelacoesDTO {
    private Long categoriaId;
    private Long fornecedorId;
    private List<Long> gruposIds;
}
