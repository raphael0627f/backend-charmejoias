package com.manager.app.preacerto;

import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoProdutoRequestDTO;
import com.manager.app.preacerto.dto.PreAcertoConsignacaoRequestDTO;
import com.manager.app.produto.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreAcertoAlteraValorTest {

    private final UUID clienteId = UUID.randomUUID();
    private final UUID produtoId = UUID.randomUUID();
    private PreAcertoConsignacao atual;
    private PreAcertoConsignacaoRequestDTO req;

    @BeforeEach
    void preparar() {
        Consignacao c = new Consignacao();
        c.setId(7L);
        Cliente cli = new Cliente();
        cli.setId(clienteId);
        Produto p = new Produto();
        p.setId(produtoId);
        PreAcertoConsignacaoProduto item = new PreAcertoConsignacaoProduto();
        item.setProduto(p);
        item.setQuantidade(2);
        item.setPreco(new BigDecimal("50.00"));
        atual = new PreAcertoConsignacao();
        atual.setConsignacao(c);
        atual.setCliente(cli);
        atual.setDesconto(new BigDecimal("10.00"));
        atual.setProdutos(new ArrayList<>(List.of(item)));

        req = new PreAcertoConsignacaoRequestDTO();
        req.setConsignacaoId(7L);
        req.setClienteId(clienteId);
        req.setDesconto(new BigDecimal("10"));
        req.setObservacoes("outra observação");
        req.setProdutos(List.of(itemReq(2, "50")));
    }

    private PreAcertoConsignacaoProdutoRequestDTO itemReq(int qtd, String preco) {
        PreAcertoConsignacaoProdutoRequestDTO i = new PreAcertoConsignacaoProdutoRequestDTO();
        i.setProdutoId(produtoId);
        i.setQuantidade(qtd);
        i.setPreco(new BigDecimal(preco));
        return i;
    }

    @Test
    void corpoIdenticoOuSoObservacaoNaoAltera() {
        assertFalse(PreAcertoConsignacaoService.alteraValor(atual, req));
    }

    @Test
    void precoDiferenteAltera() {
        req.setProdutos(List.of(itemReq(2, "55")));
        assertTrue(PreAcertoConsignacaoService.alteraValor(atual, req));
    }

    @Test
    void quantidadeDiferenteAltera() {
        req.setProdutos(List.of(itemReq(3, "50")));
        assertTrue(PreAcertoConsignacaoService.alteraValor(atual, req));
    }

    @Test
    void clienteDiferenteAltera() {
        req.setClienteId(UUID.randomUUID());
        assertTrue(PreAcertoConsignacaoService.alteraValor(atual, req));
    }

    @Test
    void descontoDiferenteAltera() {
        req.setDesconto(new BigDecimal("11"));
        assertTrue(PreAcertoConsignacaoService.alteraValor(atual, req));
    }

    @Test
    void consignacaoDiferenteAltera() {
        req.setConsignacaoId(8L);
        assertTrue(PreAcertoConsignacaoService.alteraValor(atual, req));
    }

    @Test
    void itemRemovidoOuIncluidoAltera() {
        req.setProdutos(List.of());
        assertTrue(PreAcertoConsignacaoService.alteraValor(atual, req));
        PreAcertoConsignacaoProdutoRequestDTO outro = itemReq(1, "10");
        outro.setProdutoId(UUID.randomUUID());
        req.setProdutos(List.of(itemReq(2, "50"), outro));
        assertTrue(PreAcertoConsignacaoService.alteraValor(atual, req));
    }
}
