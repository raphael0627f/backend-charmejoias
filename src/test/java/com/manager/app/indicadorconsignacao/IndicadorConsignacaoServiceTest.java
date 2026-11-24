package com.manager.app.indicadorconsignacao;

import com.manager.app.indicadorconsignacao.IndicadorConsignacaoRepository.Filtro;
import com.manager.app.indicadorconsignacao.dto.IndicadorDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// orquestração dos indicadores com o repositório simulado: filtros (cliente/cidade/rota), ranking, concentração, evolução e melhores/piores
class IndicadorConsignacaoServiceTest {

    private static final LocalDate HOJE = LocalDate.parse("2026-11-24");
    private final IndicadorConsignacaoRepository repo = mock(IndicadorConsignacaoRepository.class);
    private IndicadorConsignacaoService service;
    private final UUID ana = UUID.randomUUID(), bia = UUID.randomUUID(), cris = UUID.randomUUID();

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private Filtro filtro() {
        return new Filtro(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-11-30"), HOJE, null, null);
    }

    @BeforeEach
    void preparar() {
        service = new IndicadorConsignacaoService(repo);
        when(repo.clientes(anyCollection())).thenReturn(List.of(new IndicadorConsignacaoRepository.ClienteNome(ana, "ANA", "Cidade A"),
                new IndicadorConsignacaoRepository.ClienteNome(bia, "BIA", "Cidade B"), new IndicadorConsignacaoRepository.ClienteNome(cris, "CRIS", null)));
        when(repo.vendasPorCliente(any())).thenReturn(List.of(new IndicadorConsignacaoRepository.ClienteVendas(ana, 2, v("5000")),
                new IndicadorConsignacaoRepository.ClienteVendas(bia, 1, v("3000")), new IndicadorConsignacaoRepository.ClienteVendas(cris, 1, v("2000"))));
        when(repo.inadimplenciaPorCliente(any())).thenReturn(List.of(new IndicadorConsignacaoRepository.ClienteInad(bia, 2, v("900"))));
        when(repo.prazoPorCliente(any())).thenReturn(List.of(new IndicadorConsignacaoRepository.ClientePrazo(ana, new IndicadorConsignacaoRepository.PrazoLinha(2, 6, 1, 8, 1))));
    }

    @Test
    void filtroPadraoSaoOsUltimos12MesesEValidaDatas() {
        Filtro f = service.filtro(null, null, null, null, null, HOJE);
        assertEquals(LocalDate.parse("2025-12-01"), f.inicio());
        assertEquals(HOJE, f.fim());
        assertNull(f.ibges());
        assertThrows(ItemInvalidoException.class, () -> service.filtro(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01"), null, null, null, HOJE));
    }

    @Test
    void clienteCidadeERotaInexistentesSao404() {
        when(repo.clienteExiste(any())).thenReturn(false);
        assertThrows(ObjetoNaoEncontradoException.class, () -> service.filtro(null, null, UUID.randomUUID(), null, null, HOJE));
        when(repo.ibgesDaCidade(anyLong())).thenReturn(null);
        assertThrows(ObjetoNaoEncontradoException.class, () -> service.filtro(null, null, null, 9L, null, HOJE));
        when(repo.ibgesDaRota(anyLong())).thenReturn(null);
        assertThrows(ObjetoNaoEncontradoException.class, () -> service.filtro(null, null, null, null, 9L, HOJE));
    }

    @Test
    void cidadeERotaSeCruzamPeloIbge() {
        when(repo.ibgesDaCidade(1L)).thenReturn(List.of("111", "222"));
        when(repo.ibgesDaRota(2L)).thenReturn(List.of("222", "333"));
        assertEquals(List.of("222"), service.filtro(null, null, null, 1L, 2L, HOJE).ibges());
        assertEquals(List.of("222", "333"), service.filtro(null, null, null, null, 2L, HOJE).ibges());
        when(repo.ibgesDaCidade(3L)).thenReturn(List.of()); // cidade sem IBGE: nenhum cliente casa
        assertTrue(service.filtro(null, null, null, 3L, null, HOJE).ibges().isEmpty());
    }

    @Test
    void rankingOrdenaPorVendidoPorPadraoELimitaNoTop() {
        IndicadorDTOs.Ranking r = service.ranking(filtro(), null, null, 2);
        assertEquals(3, r.totalClientes());
        assertEquals(List.of("ANA", "BIA"), r.itens().stream().map(IndicadorDTOs.ClienteRanking::cliente).toList());
        IndicadorDTOs.ClienteRanking ana1 = r.itens().get(0);
        assertEquals(v("5000.00"), ana1.valorVendido());
        assertEquals(2L, ana1.acertos());
        assertEquals(v("3.0"), ana1.prazoMedioDias()); // 6 dias / 2 pagos
        assertEquals(2L, ana1.baseTitulosPagos());
        assertEquals(0, ana1.inadimplenciaValor().signum());
        assertEquals("Cidade A", ana1.cidade());
    }

    @Test
    void rankingPorInadimplenciaPrazoENome() {
        assertEquals("BIA", service.ranking(filtro(), "inadimplencia", null, 10).itens().get(0).cliente());
        assertEquals(v("900.00"), service.ranking(filtro(), "inadimplencia", "desc", 10).itens().get(0).inadimplenciaValor());
        // prazo médio ascendente: quem não tem pagos (nulo) vai por último
        List<String> porPrazo = service.ranking(filtro(), "prazoMedio", "asc", 10).itens().stream().map(IndicadorDTOs.ClienteRanking::cliente).toList();
        assertEquals("ANA", porPrazo.get(0));
        // por nome: A-Z por padrão
        assertEquals(List.of("ANA", "BIA", "CRIS"), service.ranking(filtro(), "cliente", null, 10).itens().stream().map(IndicadorDTOs.ClienteRanking::cliente).toList());
    }

    @Test
    void rankingPorPrazoMedioDeixaClientesSemDadoPorUltimoNasDuasDirecoes() {
        // só a ANA tem prazo; BIA e CRIS são nulos
        assertEquals("ANA", service.ranking(filtro(), "prazoMedio", "desc", 10).itens().get(0).cliente());
        assertEquals("ANA", service.ranking(filtro(), "prazoMedio", "asc", 10).itens().get(0).cliente());
        List<String> desc = service.ranking(filtro(), "prazoMedio", "desc", 10).itens().stream().map(IndicadorDTOs.ClienteRanking::cliente).toList();
        assertEquals(List.of("ANA", "BIA", "CRIS"), desc);
    }

    @Test
    void titulosDeDividaIncluemPreAcertoMesmoDepoisQueOAcertoExiste() {
        // regressão do QA 6.7: o título aberto do pré-acerto não pode sumir da inadimplência quando o acerto é criado
        String sql = IndicadorConsignacaoRepository.cteTitulos(filtro(), new org.springframework.jdbc.core.namedparam.MapSqlParameterSource());
        assertTrue(sql.contains("t.pre_acerto_id IS NOT NULL"));
        assertFalse(sql.contains("acertos_consignacao a2"));
        assertTrue(sql.contains("t.situacao <> 'CANCELADO'"));
    }

    @Test
    void periodoMaiorQueDezAnosTemMensagemFormalClara() {
        assertEquals("O período selecionado excede o limite de 10 anos. Reduza o intervalo entre as datas e tente novamente.",
                assertThrows(ItemInvalidoException.class, () -> service.filtro(LocalDate.parse("2010-01-01"), LocalDate.parse("2026-11-01"), null, null, null, HOJE)).getMessage());
    }

    @Test
    void rankingValidaParametros() {
        assertEquals("Campo de ordenação inválido.", assertThrows(ItemInvalidoException.class, () -> service.ranking(filtro(), "xpto", null, 5)).getMessage());
        assertThrows(ItemInvalidoException.class, () -> service.ranking(filtro(), "vendido", "cima", 5));
        assertThrows(ItemInvalidoException.class, () -> service.ranking(filtro(), "vendido", null, 0));
        assertThrows(ItemInvalidoException.class, () -> service.ranking(filtro(), "vendido", null, 101));
    }

    @Test
    void concentracaoTop5ComPercentuais() {
        IndicadorDTOs.Concentracao c = service.concentracao(filtro());
        assertEquals(v("10000.00"), c.valorVendidoTotal());
        assertEquals(3, c.top5().size());
        assertEquals("ANA", c.top5().get(0).cliente());
        assertEquals(v("50.0"), c.top5().get(0).percentualDoTotal());
        assertEquals(v("100.0"), c.percentualTop5()); // só 3 clientes: top 5 = 100%
        assertEquals(4L, c.base().acertos());
    }

    @Test
    void concentracaoSemVendasNaoDividePorZero() {
        when(repo.vendasPorCliente(any())).thenReturn(List.of());
        IndicadorDTOs.Concentracao c = service.concentracao(filtro());
        assertTrue(c.top5().isEmpty());
        assertNull(c.percentualTop5());
        assertEquals(0L, c.base().acertos());
    }

    @Test
    void evolucaoMensalPreencheMesesSemDados() {
        when(repo.vendasPorMes(any())).thenReturn(List.of(new IndicadorConsignacaoRepository.MesVendas("2026-09", 2, v("1000"), v("150")),
                new IndicadorConsignacaoRepository.MesVendas("2026-11", 1, v("500"), v("100"))));
        when(repo.inadimplenciaPorMes(any())).thenReturn(List.of(new IndicadorConsignacaoRepository.MesInad("2026-10", 3, v("320.50"))));
        IndicadorDTOs.Evolucao e = service.evolucaoMensal(filtro());
        assertEquals(List.of("2026-09", "2026-10", "2026-11"), e.meses().stream().map(IndicadorDTOs.Mes::mes).toList());
        assertEquals(v("15.0"), e.meses().get(0).descontoPercentual());
        assertEquals(0, e.meses().get(1).valorVendido().signum());      // outubro sem acertos
        assertNull(e.meses().get(1).descontoPercentual());              // sem vendido: nulo, não zero nem erro
        assertEquals(v("320.50"), e.meses().get(1).inadimplenciaValor());
        assertEquals(3L, e.meses().get(1).inadimplenciaTitulos());
        assertEquals(3L, e.base().acertos());
    }

    @Test
    void melhoresEPioresNaoSeRepetem() {
        List<Integer> valores = List.of(50, 10, 40, 20, 30);
        IndicadorDTOs.MelhoresPiores<Integer> r = IndicadorConsignacaoService.melhoresPiores(valores, Comparator.naturalOrder(), 2);
        assertEquals(List.of(50, 40), r.melhores());
        assertEquals(List.of(10, 20), r.piores());
        // poucos itens: tudo em melhores, nada em piores
        IndicadorDTOs.MelhoresPiores<Integer> poucos = IndicadorConsignacaoService.melhoresPiores(List.of(3, 9), Comparator.naturalOrder(), 5);
        assertEquals(List.of(9, 3), poucos.melhores());
        assertTrue(poucos.piores().isEmpty());
    }

    @Test
    void meiosDePagamentoComCreditoETaxaAbsorvida() {
        when(repo.meiosPagamento(any())).thenReturn(List.of(new IndicadorConsignacaoRepository.MeioLinha(2L, "PIX", 3, v("600"), v("0")),
                new IndicadorConsignacaoRepository.MeioLinha(5L, "Cartão de crédito", 1, v("300"), v("12.30")), new IndicadorConsignacaoRepository.MeioLinha(null, null, 1, v("100"), v("0"))));
        IndicadorDTOs.MeiosPagamento m = service.meiosPagamento(filtro());
        assertEquals(List.of("PIX", "Cartão de crédito", "Crédito do cliente"), m.meios().stream().map(IndicadorDTOs.Meio::nome).toList());
        assertEquals(v("60.0"), m.meios().get(0).percentualDoValor());
        assertEquals(v("12.30"), m.taxaClienteTotal());
        assertEquals(5L, m.base().titulos());
    }

    @Test
    void produtosCategoriasValidaLimite() {
        assertThrows(ItemInvalidoException.class, () -> service.produtosCategorias(filtro(), 0));
        assertThrows(ItemInvalidoException.class, () -> service.produtosCategorias(filtro(), 51));
    }
}
