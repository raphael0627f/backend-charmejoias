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

    @NotNull(message = "O campo Cliente é obrigatório.")
    private UUID clienteId;

    @NotNull(message = "O campo Tipo de pessoa é obrigatório.")
    private TipoPessoaSpc tipoPessoa;

    @NotNull(message = "O campo Número do contrato é obrigatório.")
    private String numeroContrato;

    @NotNull(message = "O campo Data da compra é obrigatório.")
    private LocalDate dataCompra;

    @NotNull(message = "O campo Data de vencimento é obrigatório.")
    private LocalDate dataVencimento;

    @NotNull(message = "O campo Código do tipo de devedor é obrigatório.")
    private TipoDevedorSpc codigoTipoDevedor;

    @NotNull(message = "O campo Valor do débito é obrigatório.")
    private BigDecimal valorDebito;

    @NotNull(message = "O campo Natureza da inclusão é obrigatório.")
    private Integer naturezaInclusaoId;
}
