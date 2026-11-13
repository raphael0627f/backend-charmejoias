package com.manager.app.planejadorrota.dto;

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
public class PlanejadorRotaClienteRequestDTO {

    private Long id;

    @NotNull(message = "O campo Cliente é obrigatório.")
    private UUID clienteId;

    @NotNull(message = "O campo Endereço é obrigatório.")
    private Long enderecoId;

    private Integer ordem;

    private String observacao;

    private BigDecimal valorAPagar;

    @Valid
    private List<PlanejadorRotaConsignacaoItemRequestDTO> consignacoesDeixar;

    @Valid
    private List<PlanejadorRotaConsignacaoItemRequestDTO> consignacoesTrazer;

    // opcional: pedidos SEPARADO do cliente a deixar na visita (podem coexistir com consignacoesDeixar)
    @Valid
    private List<PlanejadorRotaPedidoItemRequestDTO> pedidosDeixar;
}
