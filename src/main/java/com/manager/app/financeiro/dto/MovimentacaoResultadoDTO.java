package com.manager.app.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

// resultado de transferência, ajuste e estorno; aviso (texto formal) vem quando algum banco afetado ficou com saldo negativo
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovimentacaoResultadoDTO(List<MovimentacaoResponseDTO> movimentacoes, Long transferenciaId, String aviso) {
}
