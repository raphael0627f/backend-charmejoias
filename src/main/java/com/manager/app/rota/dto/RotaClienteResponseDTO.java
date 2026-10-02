package com.manager.app.rota.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.endereco.EnderecoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "cliente",
        "endereco",
        "ordem",
        "observacao",
        "consignacoesDeixar",
        "consignacoesTrazer",
        "valorAPagar"
})
public class RotaClienteResponseDTO {
    private Long id;
    private ClienteResumoDTO cliente;
    private EnderecoDTO endereco;
    private Integer ordem;
    private String observacao;
    private List<RotaConsignacaoResumoDTO> consignacoesDeixar;
    private List<RotaConsignacaoResumoDTO> consignacoesTrazer;
    private BigDecimal valorAPagar;
}
