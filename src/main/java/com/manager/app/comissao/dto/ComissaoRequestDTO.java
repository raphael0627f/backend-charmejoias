package com.manager.app.comissao.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ComissaoRequestDTO {

    private Long id;

    @NotNull(message = "O campo Percentual é obrigatório.")
    @DecimalMin(value = "0", inclusive = true, message = "O percentual deve ser maior ou igual a zero.")
    @DecimalMax(value = "100", inclusive = true, message = "O percentual deve ser menor ou igual a 100.")
    private BigDecimal percentual;

    @NotNull(message = "O campo Valor mínimo da faixa é obrigatório.")
    @DecimalMin(value = "0", inclusive = true, message = "O valor mínimo deve ser maior ou igual a zero.")
    private BigDecimal valorMinimo;

    @NotNull(message = "O campo Valor máximo da faixa é obrigatório.")
    private BigDecimal valorMaximo;
}
