package com.manager.app.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// forma de pagamento e centro de custo: só o nome
public record CadastroSimplesRequestDTO(
        @NotBlank(message = "O nome é obrigatório.") @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.") String nome) {
}
