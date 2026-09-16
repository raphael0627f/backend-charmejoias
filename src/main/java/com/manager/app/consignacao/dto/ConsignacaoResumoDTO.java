package com.manager.app.consignacao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.mostruario.Mostruario;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// versao enxuta da consignacao pra listagem: sem os produtos/joias completos (usados só na
// visualizacao/edicao individual, via GET /consignacoes/{id}), so os agregados que a tela de
// listagem realmente usa (itensCount, quantidadeTotal, valorTotal, quantidadePorCategoria)
@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "descricao",
        "cliente",
        "mostruario",
        "dataEntrega",
        "dataPreAcertoConsignacao",
        "dataAcertoConsignacao",
        "confirmada",
        "acertoFinalizado",
        "itensCount",
        "quantidadeTotal",
        "valorTotal",
        "quantidadePorCategoria",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy"
})
public class ConsignacaoResumoDTO {

    private Long id;
    private String descricao;
    private ClienteResumoDTO cliente;
    private Mostruario mostruario;
    private LocalDate dataEntrega;
    private LocalDate dataPreAcertoConsignacao;
    private LocalDate dataAcertoConsignacao;
    private Boolean confirmada;
    // true quando existe um Acerto de Consignação confirmado pra essa
    // consignação — "Finalizado" na listagem; caso contrário "Em aberto"
    private Boolean acertoFinalizado;
    private Integer itensCount;
    private Integer quantidadeTotal;
    private BigDecimal valorTotal;
    private List<QuantidadePorCategoriaDTO> quantidadePorCategoria;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
