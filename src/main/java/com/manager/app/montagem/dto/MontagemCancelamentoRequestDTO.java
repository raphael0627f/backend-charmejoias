package com.manager.app.montagem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MontagemCancelamentoRequestDTO {

    @NotBlank(message = "O campo Motivo é obrigatório.")
    @Size(min = 5, message = "O motivo deve ter ao menos 5 caracteres.")
    private String motivo;
}
