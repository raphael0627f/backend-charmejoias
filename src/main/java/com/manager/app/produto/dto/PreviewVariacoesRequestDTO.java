package com.manager.app.produto.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PreviewVariacoesRequestDTO {

    @NotBlank(message = "SKU pai é obrigatório")
    private String skuPai;

    @NotEmpty(message = "Ao menos uma variação deve ser selecionada")
    @Valid
    private List<VariacaoSelecionadaDTO> variacoesSelecionadas;
}
