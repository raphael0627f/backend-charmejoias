package com.manager.app.variacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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
@JsonPropertyOrder({ "id", "nome", "ativo", "itens" })
public class VariacaoDTO {

    private Long id;

    @NotBlank(message = "O campo Nome é obrigatório.")
    private String nome;

    private boolean ativo;

    @NotEmpty(message = "Informe ao menos um item.")
    @Valid
    private List<ItemVariacaoDTO> itens;
}
