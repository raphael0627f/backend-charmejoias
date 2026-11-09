package com.manager.app.agenda.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cidade.dto.CidadeResponseDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// rota dentro do marcador: {id, nome, cidades}
@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "nome", "cidades" })
public class AgendaRotaResumoDTO {

    private Long id;
    private String nome;
    private List<CidadeResponseDTO> cidades;
}
