package com.manager.app.acertoconsignacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
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
        "preAcertoId",
        "desconto",
        "observacoes",
        "confirmado",
        "produtos",
        "quantidadeProdutosAcertados",
        "quantidadeProdutosDevolvidos",
        "valorTotalProdutosAcertados",
        "valorTotalComDesconto",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy"
})
public class AcertoConsignacaoResponseDTO {

    private Long id;
    private Long consignacaoId;
    private Long preAcertoId;
    private BigDecimal desconto;
    private String observacoes;
    private Boolean confirmado;
    private List<AcertoConsignacaoProdutoResponseDTO> produtos;
    private Integer quantidadeProdutosAcertados;
    private Integer quantidadeProdutosDevolvidos;
    private BigDecimal valorTotalProdutosAcertados;
    private BigDecimal valorTotalComDesconto;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
