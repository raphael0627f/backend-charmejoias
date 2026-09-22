package com.manager.app.rota.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.endereco.EnderecoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "cliente",
        "endereco",
        "ordem",
        "observacao",
        "consignacaoDeixar",
        "consignacaoTrazer",
        "deixouReal",
        "trouxeReal"
})
public class RotaClienteResponseDTO {
    private Long id;
    private ClienteResumoDTO cliente;
    private EnderecoDTO endereco;
    private Integer ordem;
    private String observacao;
    private RotaConsignacaoResumoDTO consignacaoDeixar;
    private RotaConsignacaoResumoDTO consignacaoTrazer;
    private Boolean deixouReal;
    private Boolean trouxeReal;
}
