package com.manager.app.transportadora;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "razaoSocial",
        "documento",
        "inscricaoEstadual",
        "endereco",
        "municipio",
        "uf",
        "placaVeiculo",
        "ufVeiculo",
        "rntc",
        "ativo"
})
public class TransportadoraDTO {

    private Long id;

    @NotBlank(message = "Razão social é obrigatória")
    private String razaoSocial;

    private String documento;
    private String inscricaoEstadual;

    private String endereco;
    private String municipio;
    private String uf;

    private String placaVeiculo;
    private String ufVeiculo;
    private String rntc;

    private boolean ativo;
}
