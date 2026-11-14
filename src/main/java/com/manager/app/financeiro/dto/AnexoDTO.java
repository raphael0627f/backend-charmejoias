package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.TipoAnexo;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.time.LocalDateTime;

// baixaId só vem nos comprovantes de baixa
public record AnexoDTO(Long id, TipoAnexo tipo, String nome, String tipoConteudo, long tamanho, Long baixaId,
                       LocalDateTime createdAt, UsuarioResumoDTO createdBy) {
}
