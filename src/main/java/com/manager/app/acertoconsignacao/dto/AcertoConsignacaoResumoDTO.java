package com.manager.app.acertoconsignacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.consignacao.dto.QuantidadePorCategoriaDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// versao enxuta do acerto de consignacao pra listagem, no mesmo formato de ConsignacaoResumoDTO: sem
// os produtos completos (usados só na visualizacao/edicao individual, via GET /acertos-consignacao/{id}),
// so os agregados que a tela de listagem realmente usa
@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "consignacaoId",
        "preAcertoId",
        "cliente",
        "desconto",
        "observacoes",
        "confirmado",
        "quantidadeProdutosAcertados",
        "quantidadeProdutosDevolvidos",
        "valorTotalProdutosAcertados",
        "valorTotalComDesconto",
        "quantidadePorCategoria",
        "createdAt",
        "updatedAt"
})
public class AcertoConsignacaoResumoDTO {

    private Long id;
    private Long consignacaoId;
    private Long preAcertoId;
    private ClienteResumoDTO cliente;
    private BigDecimal desconto;
    private String observacoes;
    private Boolean confirmado;
    private Integer quantidadeProdutosAcertados;
    private Integer quantidadeProdutosDevolvidos;
    private BigDecimal valorTotalProdutosAcertados;
    private BigDecimal valorTotalComDesconto;
    private List<QuantidadePorCategoriaDTO> quantidadePorCategoria;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
