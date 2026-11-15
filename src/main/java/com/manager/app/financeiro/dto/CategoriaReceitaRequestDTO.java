package com.manager.app.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// paiId nulo = grupo (nível 1); paiId de um grupo = subgrupo (nível 2)
public record CategoriaReceitaRequestDTO(
        @NotBlank(message = "O nome é obrigatório.") @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.") String nome,
        Long paiId,
        Integer ordem) {
}
