package com.manager.app.pedido.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// linha de dados do relatório (JRBeanCollectionDataSource lê pelos getters; os nomes são os fields do .jrxml)
@Getter
@AllArgsConstructor
public class PedidoFinalLinhaDTO {
    private String codCliente;
    private String nomeCliente;
    private String municipioCliente;
    private String dataEntrega;
    private String dataCriacao;
    private String qtdTotalProdutos;
    private String qtdTotalPedidos;
    private String amarelo;
    private String rosa;
    private String laranja;
    private String verde;
}
