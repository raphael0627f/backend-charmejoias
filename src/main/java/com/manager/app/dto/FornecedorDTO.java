package com.manager.app.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "nome", "ativo" })
public class FornecedorDTO {

    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;
    private boolean ativo;
}
