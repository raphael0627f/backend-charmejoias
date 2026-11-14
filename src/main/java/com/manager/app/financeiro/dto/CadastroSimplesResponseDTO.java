package com.manager.app.financeiro.dto;

import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.time.LocalDateTime;

public record CadastroSimplesResponseDTO(Long id, String nome, boolean ativo, LocalDateTime createdAt, LocalDateTime updatedAt,
                                         UsuarioResumoDTO createdBy, UsuarioResumoDTO updatedBy) {
}
