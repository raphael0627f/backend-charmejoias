package com.manager.app.consignacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cliente.Cliente;
import com.manager.app.mostruario.Mostruario;
import com.manager.app.shared.TipoItem;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "descricao",
        "tipo",
        "cliente",
        "mostruario",
        "produtos",
        "dataEntrega",
        "dataPreAcertoConsignacao",
        "dataAcertoConsignacao",
        "confirmada",
        "cancelada",
        "motivoCancelamento",
        "canceladaEm",
        "canceladaPor",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy",
})
public class ConsignacaoResponseDTO {

    private Long id;
    private String descricao;
    private TipoItem tipo;
    private Cliente cliente;
    private Mostruario mostruario;
    private List<ConsignacaoProdutoResponseDTO> produtos;
    private LocalDate dataEntrega;
    private LocalDate dataPreAcertoConsignacao;
    private LocalDate dataAcertoConsignacao;
    private Boolean confirmada;
    private Boolean cancelada;
    private String motivoCancelamento;
    private LocalDateTime canceladaEm;
    private UsuarioResumoDTO canceladaPor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
