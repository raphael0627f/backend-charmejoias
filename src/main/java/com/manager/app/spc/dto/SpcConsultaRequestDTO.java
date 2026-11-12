package com.manager.app.spc.dto;

import com.manager.app.spc.TipoPessoaSpc;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class SpcConsultaRequestDTO {

    @NotNull(message = "O campo Cliente é obrigatório.")
    private UUID clienteId;

    @NotNull(message = "O campo Tipo de pessoa é obrigatório.")
    private TipoPessoaSpc tipoPessoa;

    @NotNull(message = "O campo Código do produto é obrigatório.")
    private Integer codigoProduto;

    private List<Integer> insumosOpcionais;
}
