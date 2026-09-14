package com.manager.app.spc.dto;

import com.manager.app.spc.TipoDevedorSpc;
import com.manager.app.spc.TipoPessoaSpc;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class SpcInclusaoRequestDTO {

    @NotNull
    private UUID clienteId;

    @NotNull
    private TipoPessoaSpc tipoPessoa;

    @NotNull
    private String numeroContrato;

    @NotNull
    private LocalDate dataCompra;

    @NotNull
    private LocalDate dataVencimento;

    @NotNull
    private TipoDevedorSpc codigoTipoDevedor;

    @NotNull
    private BigDecimal valorDebito;

    @NotNull
    private Integer naturezaInclusaoId;
}
