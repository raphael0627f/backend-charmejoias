package com.manager.app.acertoconsignacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.acertoconsignacao.AcertoConsignacaoProduto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "consignacaoId",
        "desconto",
        "observacoes",
        "confirmado",
        "produtos",
        "quantidadeProdutosAcertados",
        "quantidadeProdutosDevolvidos",
        "valorTotalProdutosAcertados",
        "valorTotalComDesconto",
        "createdAt",
        "updatedAt"
})
public class AcertoConsignacaoResponseDTO {

    private Long id;
    private Long consignacaoId;
    private BigDecimal desconto;
    private String observacoes;
    private Boolean confirmado;
    private List<AcertoConsignacaoProduto> produtos;
    private Integer quantidadeProdutosAcertados;
    private Integer quantidadeProdutosDevolvidos;
    private BigDecimal valorTotalProdutosAcertados;
    private BigDecimal valorTotalComDesconto;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
