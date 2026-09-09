package com.manager.app.produto.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class VariacaoSelecionadaDTO {

    @NotNull(message = "Variação é obrigatória")
    private Long variacaoId;

    @NotEmpty(message = "Ao menos um item deve ser selecionado")
    private List<Long> itensVariacaoIds;
}
