package com.manager.app.agenda.dto;

import com.manager.app.agenda.AgendaMarcadorTipo;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AgendaMarcadorRequestDTO {

    private Long id;

    @NotNull(message = "Data do marcador é obrigatória")
    private LocalDate data;

    // só NAO_TRABALHA (obrigatório nele); no TRABALHA o nome é o da rota e este campo é ignorado
    private String nome;

    @NotNull(message = "Tipo do marcador é obrigatório")
    private AgendaMarcadorTipo tipo;

    // obrigatório em TRABALHA, proibido em NAO_TRABALHA
    private Long rotaId;

    // só TRABALHA: cidades do dia fora da rota (sem repetidas e sem cidade da rota)
    private List<Long> cidadeExtraIds;

    private LocalDate dataPreAcerto;

    private LocalDate dataAcerto;
}
