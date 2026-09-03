package com.manager.app.consignacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.ConsignacaoProduto;
import com.manager.app.shared.TipoItem;
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
        "descricao",
        "tipo",
        "cliente",
        "produtos",
        "createdAt",
        "updatedAt"
})
public class ConsignacaoResponseDTO {

    private Long id;
    private String descricao;
    private TipoItem tipo;
    private Cliente cliente;
    private List<ConsignacaoProduto> produtos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
