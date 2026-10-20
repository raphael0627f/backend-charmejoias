package com.manager.app.montagem.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.montagem.StatusMontagem;
import com.manager.app.mostruario.dto.MostruarioResponseDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// formato enxuto da LISTAGEM de montagens: só o que a tela mostra (inclusive as quantidades de consignações e
// pedidos, em vez das listas completas). Carregar as listas de cada montagem da página (consignações com todos os
// produtos, pedidos, instruções, usuários) deixava a paginação lenta; o detalhe (GET /montagens/{id}) continua completo
@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "clienteId", "cliente", "mostruario", "dataEntrega", "status", "quantidadeConsignacoes",
        "quantidadePedidos", "createdAt", "updatedAt" })
public class MontagemResumoDTO {

    private Long id;
    private UUID clienteId;
    private ClienteResumoDTO cliente;
    private MostruarioResponseDTO mostruario;
    private LocalDate dataEntrega;
    private StatusMontagem status;
    private int quantidadeConsignacoes;
    private int quantidadePedidos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
