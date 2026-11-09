package com.manager.app.agenda.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AgendaRotaRequestDTO {

    private Long id;

    @NotBlank(message = "Nome da rota é obrigatório")
    private String nome;

    // mínimo 1, sem repetidas e todas existentes: validado no service (erro com o campo menssage)
    private List<Long> cidadeIds;
}
