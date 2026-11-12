package com.manager.app.planejadorrota.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PlanejadorRotaConsignacaoItemRequestDTO {

    @NotNull(message = "O campo Consignação é obrigatório.")
    private Long consignacaoId;

    // null/false = ainda nao confirmado - so passa a fazer sentido preencher depois,
    // quando o usuario edita a rota com o resultado real da entrega/coleta
    private Boolean confirmada;
}
