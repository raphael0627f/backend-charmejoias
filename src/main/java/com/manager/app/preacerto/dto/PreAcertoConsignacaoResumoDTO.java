package com.manager.app.preacerto.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.consignacao.dto.QuantidadePorCategoriaDTO;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// versao enxuta do pre-acerto pra listagem, no mesmo formato de ConsignacaoResumoDTO: sem os
// produtos completos (usados só na visualizacao/edicao individual, via GET /pre-acertos-consignacao/{id}),
// so os agregados que a tela de listagem realmente usa
@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "consignacaoId",
        "cliente",
        "observacoes",
        "itensCount",
        "quantidadeTotal",
        "valorTotal",
        "desconto",
        "valorTotalComDesconto",
        "quantidadePorCategoria",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy"
})
public class PreAcertoConsignacaoResumoDTO {

    private Long id;
    private Long consignacaoId;
    private ClienteResumoDTO cliente;
    private String observacoes;
    private Integer itensCount;
    private Integer quantidadeTotal;
    private BigDecimal valorTotal;
    private BigDecimal desconto;
    private BigDecimal valorTotalComDesconto;
    private List<QuantidadePorCategoriaDTO> quantidadePorCategoria;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
