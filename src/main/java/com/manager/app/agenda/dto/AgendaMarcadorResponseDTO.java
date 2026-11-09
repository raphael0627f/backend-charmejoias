package com.manager.app.agenda.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.agenda.AgendaMarcadorTipo;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.manager.app.cidade.dto.CidadeResponseDTO;
import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "data", "nome", "tipo", "rota", "cidadesExtras", "dataPreAcerto", "dataAcerto", "createdAt", "updatedAt", "createdBy", "updatedBy" })
public class AgendaMarcadorResponseDTO {

    private Long id;
    private LocalDate data;
    private String nome;
    private AgendaMarcadorTipo tipo;
    // null em NAO_TRABALHA
    private AgendaRotaResumoDTO rota;
    private List<CidadeResponseDTO> cidadesExtras;
    private LocalDate dataPreAcerto;
    private LocalDate dataAcerto;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
