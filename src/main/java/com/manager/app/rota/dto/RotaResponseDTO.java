package com.manager.app.rota.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "nome",
        "dataRota",
        "itens",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy"
})
public class RotaResponseDTO {
    private Long id;
    private String nome;
    private LocalDate dataRota;
    private List<RotaClienteResponseDTO> itens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
