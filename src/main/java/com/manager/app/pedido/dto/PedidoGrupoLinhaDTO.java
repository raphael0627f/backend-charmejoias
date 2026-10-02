package com.manager.app.pedido.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// linha de dados do relatório (JRBeanCollectionDataSource lê pelos getters; os nomes são os fields do .jrxml)
@Getter
@AllArgsConstructor
public class PedidoGrupoLinhaDTO {
    private String codCliente;
    private String nomeCliente;
    private String municipioCliente;
    private String skuProduto;
    private String nomeProduto;
    private Integer qtdProduto;
    private String precoProduto;
    private String variacaoProduto;
    private String idGrupo;
    private String nomeGrupo;
    private String dataEntrega;
    private String dataCriacao;
}
