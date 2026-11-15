package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.TipoAnexo;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.time.LocalDateTime;

// recebimentoId só vem nos comprovantes de recebimento
public record ContaReceberAnexoDTO(Long id, TipoAnexo tipo, String nome, String tipoConteudo, long tamanho, Long recebimentoId,
                                   LocalDateTime createdAt, UsuarioResumoDTO createdBy) {
}
