package com.manager.app.spc.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

// generico de proposito: cobre qualquer insumo (obrigatorio ou opcional) que venha na
// resposta do SPC, sem precisar de uma classe Java dedicada por tipo (o schema tem mais
// de 60 tipos de insumo possiveis)
@Getter
@Setter
public class SpcInsumoResultadoDTO {
    private String nome;
    private SpcResumoInsumoDTO resumo;
    private List<Map<String, Object>> detalhes;
}
