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

    @NotNull
    private UUID clienteId;

    @NotNull
    private TipoPessoaSpc tipoPessoa;

    @NotNull
    private Integer codigoProduto;

    private List<Integer> insumosOpcionais;
}
