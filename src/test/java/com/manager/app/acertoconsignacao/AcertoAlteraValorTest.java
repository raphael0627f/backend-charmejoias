package com.manager.app.acertoconsignacao;

import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoProdutoRequestDTO;
import com.manager.app.acertoconsignacao.dto.AcertoConsignacaoRequestDTO;
import com.manager.app.produto.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcertoAlteraValorTest {

    private final UUID produtoId = UUID.randomUUID();
    private AcertoConsignacao atual;
    private AcertoConsignacaoRequestDTO req;

    @BeforeEach
    void preparar() {
        Produto p = new Produto();
        p.setId(produtoId);
        AcertoConsignacaoProduto item = new AcertoConsignacaoProduto();
        item.setProduto(p);
        item.setQuantidade(2);
        atual = new AcertoConsignacao();
        atual.setDesconto(new BigDecimal("5.00"));
        atual.setProdutos(new ArrayList<>(List.of(item)));
        req = new AcertoConsignacaoRequestDTO();
        req.setDesconto(new BigDecimal("5"));
        req.setProdutos(List.of(itemReq(2)));
    }

    private AcertoConsignacaoProdutoRequestDTO itemReq(int qtd) {
        AcertoConsignacaoProdutoRequestDTO i = new AcertoConsignacaoProdutoRequestDTO();
        i.setProdutoId(produtoId);
        i.setQuantidade(qtd);
        return i;
    }

    @Test
    void corpoIdenticoNaoAltera() {
        assertFalse(AcertoConsignacaoService.alteraValor(atual, req));
    }

    @Test
    void descontoQuantidadeOuItensAlteram() {
        req.setDesconto(new BigDecimal("6"));
        assertTrue(AcertoConsignacaoService.alteraValor(atual, req));
        req.setDesconto(new BigDecimal("5"));
        req.setProdutos(List.of(itemReq(1)));
        assertTrue(AcertoConsignacaoService.alteraValor(atual, req));
        req.setProdutos(List.of());
        assertTrue(AcertoConsignacaoService.alteraValor(atual, req));
    }
}
