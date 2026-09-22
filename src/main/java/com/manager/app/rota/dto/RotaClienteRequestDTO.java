package com.manager.app.rota.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private Long consignacaoDeixarId;

    private Long consignacaoTrazerId;

    private Boolean deixouReal;

    private Boolean trouxeReal;
}
