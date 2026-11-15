package com.manager.app.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.time.LocalDateTime;
import java.util.List;

// subcategorias só vem preenchido na árvore (GET /categorias-receita); na lista plana o campo é omitido
public record CategoriaReceitaResponseDTO(Long id, String nome, Long paiId, String paiNome, int ordem,
                                          boolean ativo, LocalDateTime createdAt, LocalDateTime updatedAt,
                                          UsuarioResumoDTO createdBy, UsuarioResumoDTO updatedBy,
                                          @JsonInclude(JsonInclude.Include.NON_NULL) List<CategoriaReceitaResponseDTO> subcategorias) {
}
