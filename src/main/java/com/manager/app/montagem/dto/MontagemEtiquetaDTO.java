package com.manager.app.montagem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// bean da fonte de dados do relatório relEtiquetaMontagemMostruario: os nomes dos atributos
// precisam ser exatamente os nomes dos fields do .jrxml (todos String)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MontagemEtiquetaDTO {
    private String codCliente;
    private String nomeContatoCliente;
    private String mostruario;
    private String dataEntrega;
    private String dataAcerto;
    private String refCliente;
    private String enderecos;
    private String nomeBairroCidadeCliente;
    private String sobrenomeCliente;
    private String cidadeCliente;
}
