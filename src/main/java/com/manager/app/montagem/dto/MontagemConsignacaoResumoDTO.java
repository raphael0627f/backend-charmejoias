package com.manager.app.montagem.dto;

import com.manager.app.shared.TipoItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class MontagemConsignacaoResumoDTO {
    private Long id;
    private String descricao;
    private TipoItem tipo;
    private LocalDate dataEntrega;
    private Boolean confirmada;
    private Integer quantidadeTotal;
    private BigDecimal valorTotal;
}
