package com.manager.app.dto;

import jakarta.validation.constraints.NotBlank;


public class GrupoDTO {

    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;
    private boolean ativo;

}
