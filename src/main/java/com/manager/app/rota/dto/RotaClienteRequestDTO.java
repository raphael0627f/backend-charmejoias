package com.manager.app.rota.dto;

import jakarta.validation.Valid;
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
public class RotaClienteRequestDTO {

    private Long id;

    @NotNull(message = "clienteId é obrigatório")
    private UUID clienteId;

    @NotNull(message = "enderecoId é obrigatório")
    private Long enderecoId;

    private Integer ordem;

    private String observacao;

    private BigDecimal valorAPagar;

    @Valid
    private List<RotaConsignacaoItemRequestDTO> consignacoesDeixar;

    @Valid
    private List<RotaConsignacaoItemRequestDTO> consignacoesTrazer;
}
