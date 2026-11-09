package com.manager.app.agenda.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cidade.dto.CidadeResponseDTO;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "nome", "cidades", "createdAt", "updatedAt", "createdBy", "updatedBy" })
public class AgendaRotaResponseDTO {

    private Long id;
    private String nome;
    private List<CidadeResponseDTO> cidades;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
