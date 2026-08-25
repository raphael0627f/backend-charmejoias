package com.manager.app.dto.joia;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.entity.Categoria;
import com.manager.app.entity.Grupo;
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
