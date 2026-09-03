package com.manager.app.joia.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.categoria.Categoria;
import com.manager.app.grupo.Grupo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "codigo",
        "referencia",
        "nome",
        "precoVenda",
        "ativo",
        "observacoes",
        "categoria",
        "grupos",
        "createdAt",
        "updatedAt"
})
public class JoiaResponseDTO {

    private UUID id;
    private String codigo;
    private String referencia;
    private String nome;
    private BigDecimal precoVenda;
    private boolean ativo;
    private String observacoes;
    private Categoria categoria;
    private List<Grupo> grupos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
