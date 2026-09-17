package com.manager.app.endereco;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class EnderecoDTO {

    private Long id;
    @NotBlank(message = "Logradouro é obrigatório")
    private String logradouro;

    private String numero;
    private String complemento;

    @NotBlank(message = "Logradouro é obrigatório")
    private String bairro;

    @NotBlank(message = "Município é obrigatório")
    private String municipio;

    @NotBlank(message = "UF é obrigatório")
    private String uf;

    @NotBlank(message = "CEP é obrigatório")
    private String cep;

    private String ibge;
    private boolean principal;

    private String pontoReferencia;
    private String descricao;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
