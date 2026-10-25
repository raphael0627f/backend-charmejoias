package com.manager.app.agenda.dto;

import com.manager.app.agenda.AgendaMarcadorTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class AgendaMarcadorRequestDTO {

    private Long id;

    @NotNull(message = "Data do marcador é obrigatória")
    private LocalDate data;

    @NotBlank(message = "Nome do marcador é obrigatório")
    private String nome;

    @NotNull(message = "Tipo do marcador é obrigatório")
    private AgendaMarcadorTipo tipo;

    private LocalDate dataConferencia;

    private LocalDate dataAcerto;
}
