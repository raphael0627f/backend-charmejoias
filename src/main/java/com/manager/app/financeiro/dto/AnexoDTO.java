package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.TipoAnexo;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.time.LocalDateTime;

// pagamentoId só vem nos comprovantes de pagamento
public record AnexoDTO(Long id, TipoAnexo tipo, String nome, String tipoConteudo, long tamanho, Long pagamentoId,
                       LocalDateTime createdAt, UsuarioResumoDTO createdBy) {
}
