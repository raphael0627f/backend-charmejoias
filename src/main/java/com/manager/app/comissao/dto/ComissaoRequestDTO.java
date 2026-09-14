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

    @NotNull(message = "Percentual é obrigatório")
    @DecimalMin(value = "0", inclusive = true, message = "Percentual deve ser maior ou igual a zero")
    @DecimalMax(value = "100", inclusive = true, message = "Percentual deve ser menor ou igual a 100")
    private BigDecimal percentual;

    @NotNull(message = "Valor mínimo do range é obrigatório")
    @DecimalMin(value = "0", inclusive = true, message = "Valor mínimo deve ser maior ou igual a zero")
    private BigDecimal valorMinimo;

    @NotNull(message = "Valor máximo do range é obrigatório")
    private BigDecimal valorMaximo;
}
