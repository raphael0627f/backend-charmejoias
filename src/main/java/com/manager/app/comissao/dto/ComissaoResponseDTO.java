package com.manager.app.comissao.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "percentual", "valorMinimo", "valorMaximo", "createdAt", "updatedAt" })
public class ComissaoResponseDTO {

    private Long id;
    private BigDecimal percentual;
    private BigDecimal valorMinimo;
    private BigDecimal valorMaximo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
