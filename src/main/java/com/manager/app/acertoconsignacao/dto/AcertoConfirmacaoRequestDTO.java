package com.manager.app.acertoconsignacao.dto;

// corpo opcional de PUT /acertos-consignacao/{id}/confirmar: aceite explícito do ajuste do título do pré-acerto (opção B)
public record AcertoConfirmacaoRequestDTO(Boolean ajustarTituloPreAcerto) {
}
