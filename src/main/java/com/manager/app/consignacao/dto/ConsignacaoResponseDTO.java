package com.manager.app.consignacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.ConsignacaoProduto;
import com.manager.app.mostruario.Mostruario;
import com.manager.app.shared.TipoItem;
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
        "createdAt",
        "updatedAt"
})
public class ConsignacaoResponseDTO {

    private Long id;
    private String descricao;
    private TipoItem tipo;
    private Cliente cliente;
    private Mostruario mostruario;
    private List<ConsignacaoProduto> produtos;
    private LocalDate dataEntrega;
    private LocalDate dataPreAcertoConsignacao;
    private LocalDate dataAcertoConsignacao;
    private Boolean confirmada;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
