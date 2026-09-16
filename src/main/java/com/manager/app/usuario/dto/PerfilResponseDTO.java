package com.manager.app.usuario.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.usuario.Permissao;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "nome", "permissoes", "createdAt", "updatedAt", "createdBy", "updatedBy" })
public class PerfilResponseDTO {
    private Long id;
    private String nome;
    private Set<Permissao> permissoes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
