package com.manager.app.ncm.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "codigo", "descricao", "ativo", "aliquotaAproximadaTributos" })
public class NcmDTO {

    @NotBlank(message = "Código é obrigatório")
    @Pattern(regexp = "\\d{8}", message = "Código NCM deve ter 8 dígitos")
    private String codigo;

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    private boolean ativo;

    private BigDecimal aliquotaAproximadaTributos;
}
