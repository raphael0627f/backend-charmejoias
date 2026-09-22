package com.manager.app.rota.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// versao enxuta da rota pra listagem, no mesmo formato de ConsignacaoResumoDTO/AcertoConsignacaoResumoDTO
@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "nome",
        "dataRota",
        "quantidadeClientes",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy"
})
public class RotaResumoDTO {
    private Long id;
    private String nome;
    private LocalDate dataRota;
    private Integer quantidadeClientes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
