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

    @NotNull(message = "O campo Variação é obrigatório.")
    private Long variacaoId;

    @NotEmpty(message = "Selecione ao menos um item.")
    private List<Long> itensVariacaoIds;
}
