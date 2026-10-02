package com.manager.app.preacerto.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PreAcertoConsignacaoRequestDTO {

    private Long id;
    private Long consignacaoId;
    private UUID clienteId;
    private String observacoes;
    private BigDecimal desconto;

    @NotNull(message = "Este Pré-acerto precisa ter um ou mais Produtos")
    private List<PreAcertoConsignacaoProdutoRequestDTO> produtos;
}
