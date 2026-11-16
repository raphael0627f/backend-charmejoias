package com.manager.app.infinitepay;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LinkPagamentoCalculoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);

    private static LinkPagamentoCalculo.Dados dados(String valor, LocalDate validade, boolean pix, String descPix, String descCartao, int taxa) {
        return new LinkPagamentoCalculo.Dados(new BigDecimal(valor), "Consignação Nº 12 — acerto", validade, true, taxa, pix,
                new BigDecimal(descPix), new BigDecimal(descCartao), false, new LinkPagamentoCalculo.Cliente("Maria", "maria@exemplo.com", "+5524999998888"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pedidoUsaCentavosInteirosEOpcoes() {
        Map<String, Object> p = LinkPagamentoCalculo.montarPedido("minha-tag", "CR-31-abc12345", dados("250.50", HOJE.plusDays(7), true, "0", "0", 3));
        assertEquals("minha-tag", p.get("handle"));
        assertEquals("CR-31-abc12345", p.get("order_nsu"));
        Map<String, Object> item = ((List<Map<String, Object>>) p.get("items")).get(0);
        assertEquals(1, item.get("quantity"));
        assertEquals(25050L, item.get("price"));
        assertEquals("Consignação Nº 12 — acerto", item.get("description"));
        Map<String, Object> o = (Map<String, Object>) p.get("options");
        assertEquals(3, o.get("assured_installments"));
        assertEquals(true, o.get("single_payment"));
        assertEquals(false, o.get("require_delivery_address"));
        assertEquals("2026-11-23T23:59:59-03:00", o.get("valid_until"));
        assertFalse(o.containsKey("payment_methods_enabled"));
        assertFalse(o.containsKey("discounts"));
        Map<String, Object> c = (Map<String, Object>) p.get("customer");
        assertEquals("Maria", c.get("name"));
        assertEquals("+5524999998888", c.get("phone_number"));
        assertFalse(p.containsKey("redirect_url"));
        assertFalse(p.containsKey("webhook"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pixDesligadoDescontosESemValidade() {
        Map<String, Object> p = LinkPagamentoCalculo.montarPedido("t", "n", dados("10.00", null, false, "0", "5.5", 0));
        Map<String, Object> o = (Map<String, Object>) p.get("options");
        assertFalse(o.containsKey("valid_until"));
        assertEquals(List.of("credit_card"), o.get("payment_methods_enabled"));
        Map<String, Object> d = (Map<String, Object>) o.get("discounts");
        assertEquals(1, d.size());
        assertEquals(5.5, d.get("credit_card"));
        Map<String, Object> p2 = LinkPagamentoCalculo.montarPedido("t", "n", dados("10.00", null, true, "10", "0", 0));
        assertEquals(10.0, ((Map<String, Object>) ((Map<String, Object>) p2.get("options")).get("discounts")).get("pix"));
    }

    @Test
    void pedidoSemClienteNaoEnviaCustomer() {
        LinkPagamentoCalculo.Dados d = new LinkPagamentoCalculo.Dados(new BigDecimal("5.00"), "x", null, true, 0, true, BigDecimal.ZERO, BigDecimal.ZERO, false,
                new LinkPagamentoCalculo.Cliente(null, null, null));
        assertFalse(LinkPagamentoCalculo.montarPedido("t", "n", d).containsKey("customer"));
    }

    @Test
    void centavosArredondaEmDuasCasas() {
        assertEquals(100L, LinkPagamentoCalculo.centavos(new BigDecimal("1")));
        assertEquals(123456L, LinkPagamentoCalculo.centavos(new BigDecimal("1234.56")));
    }

    @Test
    void valorDeUmRealAteOSaldo() {
        BigDecimal saldo = new BigDecimal("250.00");
        assertEquals(new BigDecimal("250.00"), LinkPagamentoCalculo.validarValor(null, saldo));
        assertEquals(new BigDecimal("1.00"), LinkPagamentoCalculo.validarValor(new BigDecimal("1"), saldo));
        assertEquals(new BigDecimal("250.00"), LinkPagamentoCalculo.validarValor(new BigDecimal("250"), saldo));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarValor(new BigDecimal("0.99"), saldo));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarValor(new BigDecimal("250.01"), saldo));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarValor(new BigDecimal("10.123"), saldo));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarValor(null, new BigDecimal("0.50")));
    }

    @Test
    void validadeFuturaAteUmAno() {
        assertNull(LinkPagamentoCalculo.validarValidade(null, HOJE));
        assertEquals(HOJE.plusDays(1), LinkPagamentoCalculo.validarValidade(HOJE.plusDays(1), HOJE));
        assertEquals(HOJE.plusYears(1), LinkPagamentoCalculo.validarValidade(HOJE.plusYears(1), HOJE));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarValidade(HOJE, HOJE));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarValidade(HOJE.minusDays(1), HOJE));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarValidade(HOJE.plusYears(1).plusDays(1), HOJE));
    }

    @Test
    void taxaDeZeroADoze() {
        assertEquals(0, LinkPagamentoCalculo.validarTaxa(null));
        assertEquals(12, LinkPagamentoCalculo.validarTaxa(12));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarTaxa(13));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarTaxa(-1));
    }

    @Test
    void descontoMaiorQueZeroEMenorQueCem() {
        assertEquals(0, LinkPagamentoCalculo.validarDesconto("do PIX", null).signum());
        assertEquals(0, LinkPagamentoCalculo.validarDesconto("do PIX", BigDecimal.ZERO).signum());
        assertEquals(new BigDecimal("99.99"), LinkPagamentoCalculo.validarDesconto("do PIX", new BigDecimal("99.99")));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarDesconto("do PIX", new BigDecimal("100")));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarDesconto("do PIX", new BigDecimal("-1")));
    }

    @Test
    void descricaoObrigatoriaAte200ComPadrao() {
        assertEquals("Padrão", LinkPagamentoCalculo.validarDescricao("  ", "Padrão"));
        assertEquals("Minha", LinkPagamentoCalculo.validarDescricao(" Minha ", "Padrão"));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarDescricao(null, null));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarDescricao("a".repeat(201), "x"));
    }

    @Test
    void telefoneNormalizadoParaMaisCinquentaECinco() {
        assertEquals("+5524999998888", LinkPagamentoCalculo.normalizarTelefone("(24) 99999-8888"));
        assertEquals("+5524999998888", LinkPagamentoCalculo.normalizarTelefone("+55 24 99999-8888"));
        assertEquals("+552433445566", LinkPagamentoCalculo.normalizarTelefone("(24) 3344-5566"));
        assertNull(LinkPagamentoCalculo.normalizarTelefone("99999-8888"));
        assertNull(LinkPagamentoCalculo.normalizarTelefone("(24) 89999-8888"));
        assertNull(LinkPagamentoCalculo.normalizarTelefone("(00) 99999-8888"));
        assertNull(LinkPagamentoCalculo.normalizarTelefone("abc"));
        assertNull(LinkPagamentoCalculo.normalizarTelefone(null));
        // celular preferido; sem celular válido, telefone fixo
        assertEquals("+5524999998888", LinkPagamentoCalculo.telefoneDoCadastro("24999998888", "2433445566"));
        assertEquals("+552433445566", LinkPagamentoCalculo.telefoneDoCadastro("123", "(24) 3344-5566"));
        assertNull(LinkPagamentoCalculo.telefoneDoCadastro(null, null));
    }

    @Test
    void emailBasicoEDadosInvalidosOmitidos() {
        assertTrue(LinkPagamentoCalculo.emailValido("maria@exemplo.com"));
        assertTrue(LinkPagamentoCalculo.emailValido(" maria.silva+x@mail.exemplo.com.br "));
        assertFalse(LinkPagamentoCalculo.emailValido("maria@exemplo"));
        assertFalse(LinkPagamentoCalculo.emailValido("maria exemplo@x.com"));
        assertFalse(LinkPagamentoCalculo.emailValido("a@@b.com"));
        assertFalse(LinkPagamentoCalculo.emailValido(null));
        LinkPagamentoCalculo.Cliente c = LinkPagamentoCalculo.normalizarCliente("  ", "invalido", "123");
        assertTrue(c.vazio());
        LinkPagamentoCalculo.Cliente ok = LinkPagamentoCalculo.normalizarCliente(" Maria ", "m@x.com", "24999998888");
        assertEquals("Maria", ok.nome());
        assertEquals("m@x.com", ok.email());
        assertEquals("+5524999998888", ok.telefone());
    }

    @Test
    void extraiUrlDeVariantesDaResposta() throws Exception {
        var m = JsonMapper.builder().build();
        assertEquals("https://x.com/a", LinkPagamentoCalculo.extrairUrl(m.readTree("{\"url\":\"https://x.com/a\"}"), null));
        assertEquals("https://x.com/b", LinkPagamentoCalculo.extrairUrl(m.readTree("{\"link\":\"https://x.com/b\"}"), null));
        assertEquals("https://x.com/c", LinkPagamentoCalculo.extrairUrl(m.readTree("{\"data\":{\"checkout_url\":\"https://x.com/c\"}}"), null));
        assertEquals("https://x.com/d", LinkPagamentoCalculo.extrairUrl(m.readTree("{\"link\":{\"url\":\"https://x.com/d\"}}"), null));
        assertEquals("https://x.com/e", LinkPagamentoCalculo.extrairUrl(null, "  https://x.com/e \n"));
        assertNull(LinkPagamentoCalculo.extrairUrl(m.readTree("{\"mensagem\":\"ok\"}"), "{\"mensagem\":\"ok\"}"));
        assertNull(LinkPagamentoCalculo.extrairUrl(m.readTree("{\"url\":\"nao-e-url\"}"), null));
    }

    @Test
    void chaveMascaradaEHandleLimpo() {
        assertEquals("****wxyz", LinkPagamentoCalculo.ultimosCaracteres("inchk_abcdefghijklwxyz"));
        assertEquals("****", LinkPagamentoCalculo.ultimosCaracteres("curta"));
        assertNull(LinkPagamentoCalculo.ultimosCaracteres(null));
        assertEquals("minha-tag", LinkPagamentoCalculo.limparHandle(" $minha-tag "));
        assertNull(LinkPagamentoCalculo.limparHandle(" $ "));
        assertDoesNotThrow(() -> LinkPagamentoCalculo.validarHandle("minha.tag_1-x"));
        assertThrows(ItemInvalidoException.class, () -> LinkPagamentoCalculo.validarHandle("minha tag"));
    }

    @Test
    void segredoEcoadoNoCorpoEOcultadoNoLog() {
        String corpo = "{\"erro\":\"x\",\"chave\":\"inchk_abc123\",\"de_novo\":\"inchk_abc123\"}";
        String saida = LinkPagamentoCalculo.ocultarSegredo(corpo, "inchk_abc123", 300);
        assertFalse(saida.contains("inchk_abc123"));
        assertTrue(saida.contains("****"));
        assertEquals(10, LinkPagamentoCalculo.ocultarSegredo("a".repeat(50), "k", 10).length());
        assertNull(LinkPagamentoCalculo.ocultarSegredo(null, "k", 10));
    }

    @Test
    void textosSubstituemMarcadores() {
        String t = LinkPagamentoCalculo.renderizar("{cliente}|{valor}|{descricao}|{link}|{validade}", "padrao", "Maria", new BigDecimal("1234.50"), "Acerto", "https://x.com/a", LocalDate.of(2026, 12, 1));
        assertEquals("Maria|R$ 1.234,50|Acerto|https://x.com/a|até 01/12/2026", t);
        String sem = LinkPagamentoCalculo.renderizar(null, "Olá {cliente} {validade}", null, BigDecimal.ONE, "x", "l", null);
        assertEquals("Olá cliente sem data de expiração", sem);
    }

    @Test
    void duploCliqueComparaTodosOsDados() {
        LinkPagamentoCalculo.Dados d = dados("100.00", HOJE.plusDays(7), true, "0", "0", 0);
        ContaReceberLinkPagamento l = new ContaReceberLinkPagamento();
        l.setValor(new BigDecimal("100.0"));
        l.setDescricao(d.descricao());
        l.setValidade(d.validade());
        l.setPagamentoUnico(true);
        l.setAssuredInstallments(0);
        l.setPixHabilitado(true);
        l.setDescontoPix(new BigDecimal("0.00"));
        l.setDescontoCartao(BigDecimal.ZERO);
        l.setExigeEndereco(false);
        l.setClienteNome("Maria");
        l.setClienteEmail("maria@exemplo.com");
        l.setClienteTelefone("+5524999998888");
        assertTrue(LinkPagamentoCalculo.mesmosDados(d, l));
        l.setValor(new BigDecimal("99.00"));
        assertFalse(LinkPagamentoCalculo.mesmosDados(d, l));
        l.setValor(new BigDecimal("100.00"));
        l.setClienteEmail(null);
        assertFalse(LinkPagamentoCalculo.mesmosDados(d, l));
    }
}
