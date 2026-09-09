package com.manager.app.variacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "nome", "valor", "ativo" })
public class ItemVariacaoDTO {

    private Long id;

    @NotBlank(message = "Nome do item é obrigatório")
    private String nome;

    @NotBlank(message = "Valor do item é obrigatório")
    private String valor;

    private boolean ativo;
}
