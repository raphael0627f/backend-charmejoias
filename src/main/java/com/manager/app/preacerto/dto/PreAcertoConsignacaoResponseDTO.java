package com.manager.app.preacerto.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cliente.Cliente;
import com.manager.app.preacerto.PreAcertoConsignacaoProduto;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "consignacaoId",
        "cliente",
        "observacoes",
        "produtos",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy"
})
public class PreAcertoConsignacaoResponseDTO {

    private Long id;
    private Long consignacaoId;
    private Cliente cliente;
    private String observacoes;
    private List<PreAcertoConsignacaoProduto> produtos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
