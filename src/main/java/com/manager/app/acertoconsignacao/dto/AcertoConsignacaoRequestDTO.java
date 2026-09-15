package com.manager.app.acertoconsignacao.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AcertoConsignacaoRequestDTO {

    private Long id;
    private Long consignacaoId;
    private Long preAcertoId;
    private BigDecimal desconto;
    private String observacoes;

    @NotNull(message = "Este Acerto precisa ter um ou mais Produtos devolvidos")
    private List<AcertoConsignacaoProdutoRequestDTO> produtos;
}
