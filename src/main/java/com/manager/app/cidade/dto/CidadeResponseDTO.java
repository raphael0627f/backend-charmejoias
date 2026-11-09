package com.manager.app.cidade.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cidade.CidadeTipo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "nome", "uf", "ibge", "tipo", "municipioNome", "descricao" })
public class CidadeResponseDTO {

    private Long id;
    private String nome;
    private String uf;
    private String ibge;
    private CidadeTipo tipo;
    private String municipioNome;
    // "Nome - UF" ou, para distrito, "Nome (distrito de Município) - UF"
    private String descricao;
}
