package com.manager.app.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.manager.app.financeiro.TipoCategoriaDespesa;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.time.LocalDateTime;
import java.util.List;

// subcategorias só vem preenchido na árvore (GET /categorias-despesa); na lista plana o campo é omitido
public record CategoriaDespesaResponseDTO(Long id, String nome, Long paiId, String paiNome, TipoCategoriaDespesa tipo, int ordem,
                                          boolean ativo, LocalDateTime createdAt, LocalDateTime updatedAt,
                                          UsuarioResumoDTO createdBy, UsuarioResumoDTO updatedBy,
                                          @JsonInclude(JsonInclude.Include.NON_NULL) List<CategoriaDespesaResponseDTO> subcategorias) {
}
