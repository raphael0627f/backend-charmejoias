package com.manager.app.mostruario.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MostruarioRequestDTO {

    private Long id;

    @NotBlank(message = "O campo Descrição é obrigatório.")
    private String descricao;
}
