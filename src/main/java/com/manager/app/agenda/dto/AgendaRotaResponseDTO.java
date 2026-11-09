package com.manager.app.agenda.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cidade.dto.CidadeResponseDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "nome", "cidades", "createdAt", "updatedAt" })
public class AgendaRotaResponseDTO {

    private Long id;
    private String nome;
    private List<CidadeResponseDTO> cidades;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
