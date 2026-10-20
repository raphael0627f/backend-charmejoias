package com.manager.app.montagem.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.montagem.StatusMontagem;
import com.manager.app.mostruario.dto.MostruarioResponseDTO;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "clienteId", "cliente", "mostruario", "instrucoes", "dataEntrega", "status", "motivoCancelamento",
        "iniciadaEm", "iniciadaPor", "concluidaEm", "consignacoes", "pedidos", "createdAt", "updatedAt", "createdBy", "updatedBy" })
public class MontagemResponseDTO {

    private Long id;
    private UUID clienteId;
    private ClienteResumoDTO cliente;
    private MostruarioResponseDTO mostruario;
    private List<MontagemInstrucaoResponseDTO> instrucoes;
    private LocalDate dataEntrega;
    private StatusMontagem status;
    private String motivoCancelamento;
    private LocalDateTime iniciadaEm;
    private UsuarioResumoDTO iniciadaPor;
    private LocalDateTime concluidaEm;
    private List<MontagemConsignacaoResumoDTO> consignacoes;
    private List<MontagemPedidoResumoDTO> pedidos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
