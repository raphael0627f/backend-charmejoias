package com.manager.app.pedido.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// linha de dados do relatório relPedidoIndividual (uma por item; o relatório agrupa por idProduto (que aqui é o id do pedido) e
// quebra a página a cada pedido) - JRBeanCollectionDataSource lê pelos getters; os nomes são os
// fields do .jrxml
@Getter
@AllArgsConstructor
public class PedidoIndividualLinhaDTO {
    private Long idProduto;
    private String categoriaPedido;
    private String statusPedido;
    private String codCliente;
    private String nomeCliente;
    private String municipioCliente;
    private String dataEntrega;
    private String observacao;
    private String skuProduto;
    private String nomeProduto;
    private String variacaoProduto;
    private Integer quantidadeProduto;
    private String precoProduto;
    private String totalProduto;
    private Integer quantidadeTotalProduto;
    private String valorTotalPedido;
}
