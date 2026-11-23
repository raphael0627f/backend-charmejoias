package com.manager.app.infinitepay;

import com.manager.app.financeiro.Banco;
import com.manager.app.financeiro.ContaReceber;
import com.manager.app.financeiro.ContaReceberRecebimento;
import com.manager.app.financeiro.ContaReceberRecebimentoService;
import com.manager.app.financeiro.ContaReceberService;
import com.manager.app.financeiro.FormaPagamento;
import com.manager.app.financeiro.SituacaoContaReceber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// processamento da fila com receptor, InfinitePay e repositórios simulados (sem rede nem banco): idempotência, payment_check, valores, duplicidade,
// link desconhecido, eventos de estorno, falha de rede sem ack e serialização de duas buscas simultâneas
class BaixaAutomaticaServiceTest {

    private final ReceptorClient receptor = mock(ReceptorClient.class);
    private final InfinitePayAutomaticoService automatico = mock(InfinitePayAutomaticoService.class);
    private final InfinitePayConfiguracaoService configuracao = mock(InfinitePayConfiguracaoService.class);
    private final InfinitePayClient client = mock(InfinitePayClient.class);
    private final InfinitePayEventoRepository eventos = mock(InfinitePayEventoRepository.class);
    private final ContaReceberLinkPagamentoRepository links = mock(ContaReceberLinkPagamentoRepository.class);
    private final ContaReceberService contaReceberService = mock(ContaReceberService.class);
    private final ContaReceberRecebimentoService recebimentoService = mock(ContaReceberRecebimentoService.class);
    private final TravaAdvisory trava = mock(TravaAdvisory.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);

    private final Map<Long, InfinitePayEvento> banco = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private BaixaAutomaticaService service;
    private ContaReceber titulo;
    private ContaReceberLinkPagamento link;

    @BeforeEach
    void preparar() {
        when(tm.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(eventos.findByTransactionNsuAndEvento(anyString(), anyString())).thenAnswer(i ->
                banco.values().stream().filter(e -> e.getTransactionNsu().equals(i.getArgument(0)) && e.getEvento().equals(i.getArgument(1))).findFirst());
        when(eventos.saveAndFlush(any())).thenAnswer(i -> guardar(i.getArgument(0)));
        when(eventos.save(any())).thenAnswer(i -> guardar(i.getArgument(0)));
        when(eventos.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(banco.get(i.<Long>getArgument(0))));
        when(eventos.findByStatusInOrderByIdAsc(any())).thenAnswer(i -> {
            java.util.Collection<InfinitePayEvento.Status> st = i.getArgument(0);
            return banco.values().stream().filter(e -> st.contains(e.getStatus())).sorted(java.util.Comparator.comparing(InfinitePayEvento::getId)).toList();
        });
        when(eventos.countByStatus(any())).thenAnswer(i -> banco.values().stream().filter(e -> e.getStatus() == i.getArgument(0)).count());

        Banco b = new Banco();
        b.setId(1L);
        b.setNome("Banco teste");
        b.setAtivo(true);
        titulo = new ContaReceber();
        titulo.setId(7L);
        titulo.setValor(new BigDecimal("15.00"));
        titulo.setValorRecebido(BigDecimal.ZERO);
        titulo.setSituacao(SituacaoContaReceber.ABERTO);
        link = new ContaReceberLinkPagamento();
        link.setId(5L);
        link.setContaReceber(titulo);
        link.setOrderNsu("CR-7-0");
        link.setValor(new BigDecimal("15.00"));
        link.setModo(ModoLinkPagamento.AUTOMATICO);
        link.setStatus(StatusLinkPagamento.ATIVO);
        link.setBanco(b);
        when(links.findIdByOrderNsu("CR-7-0")).thenReturn(Optional.of(5L));
        when(links.findById(5L)).thenReturn(Optional.of(link));
        when(contaReceberService.buscarComLock(7L)).thenReturn(titulo);

        when(automatico.receptor()).thenReturn(new InfinitePayAutomaticoService.Receptor("https://pagamentos.exemplo.com.br", "segredo"));
        when(automatico.baixaAutomaticaAtiva()).thenReturn(true);
        FormaPagamento cartao = new FormaPagamento();
        cartao.setId(11L);
        cartao.setNome("Cartão de crédito");
        when(automatico.formaPorCaptura(anyString())).thenReturn(cartao);
        when(configuracao.credenciais()).thenReturn(new InfinitePayConfiguracaoService.Credenciais("tag", "chave"));
        when(recebimentoService.receberAutomatico(any(), anyLong(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            ContaReceberRecebimento r = new ContaReceberRecebimento();
            r.setId(99L);
            titulo.setSituacao(SituacaoContaReceber.RECEBIDO);
            titulo.setValorRecebido(i.getArgument(4));
            return r;
        });
        service = new BaixaAutomaticaService(receptor, automatico, configuracao, client, eventos, links, contaReceberService, recebimentoService, trava,
                JsonMapper.builder().build(), tm);
    }

    private InfinitePayEvento guardar(InfinitePayEvento e) {
        if (e.getId() == null) e.setId(seq.incrementAndGet());
        banco.put(e.getId(), e);
        return e;
    }

    private static String pagamento(String evento, String order, String nsu, long amount, long pago) {
        return "{\"event\":\"" + evento + "\",\"invoice_slug\":\"slug\",\"amount\":" + amount + ",\"paid_amount\":" + pago
                + ",\"installments\":3,\"capture_method\":\"credit_card\",\"transaction_nsu\":\"" + nsu + "\",\"order_nsu\":\"" + order + "\"}";
    }

    private static ReceptorClient.ItemFila item(long id, String evento, String corpo, String nsu) {
        return new ReceptorClient.ItemFila(id, "2026-11-23T10:00:00Z", evento, "CR-7-0", nsu, "slug", corpo);
    }

    private void fila(ReceptorClient.ItemFila... itens) {
        when(receptor.buscarFila(anyInt())).thenReturn(List.of(itens)).thenReturn(List.of());
    }

    private void confirmaPago(long amount, long pago) {
        doReturn(new InfinitePayClient.PagamentoVerificado(true, true, amount, pago, 3, "credit_card")).when(client)
                .verificarPagamento(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private InfinitePayEvento unico() {
        assertEquals(1, banco.size());
        return banco.values().iterator().next();
    }

    @Test
    void pagamentoConfirmadoBaixaPeloValorDoTituloEGuardaATaxa() {
        fila(item(1, "paid", pagamento("paid", "CR-7-0", "T1", 1500, 1510), "T1"));
        confirmaPago(1500, 1510);
        BaixaAutomaticaService.Resumo r = service.ciclo();
        assertEquals(1, r.getBaixados());
        assertEquals(InfinitePayEvento.Status.PROCESSADO, unico().getStatus());
        verify(recebimentoService).receberAutomatico(eq(titulo), eq(1L), any(), any(), eq(new BigDecimal("15.00")), eq(new BigDecimal("0.10")), anyString(), eq(link), eq("T1"));
        verify(receptor).ack(List.of(1L));
    }

    @Test
    void valorPagoIgualAoCobradoNaoTemTaxa() {
        fila(item(1, "paid", pagamento("paid", "CR-7-0", "T1", 1500, 1500), "T1"));
        confirmaPago(1500, 1500);
        service.ciclo();
        verify(recebimentoService).receberAutomatico(any(), anyLong(), any(), any(), eq(new BigDecimal("15.00")), eq(new BigDecimal("0.00")), anyString(), any(), anyString());
    }

    @Test
    void valorPagoMenorVaiParaRevisaoSemBaixar() {
        fila(item(1, "paid", pagamento("paid", "CR-7-0", "T1", 1500, 1400), "T1"));
        confirmaPago(1500, 1400);
        BaixaAutomaticaService.Resumo r = service.ciclo();
        assertEquals(1, r.getRevisao());
        assertEquals(InfinitePayEvento.Status.REVISAO, unico().getStatus());
        assertTrue(unico().getMotivo().contains("menor"));
        verifyNoInteractions(recebimentoService);
    }

    @Test
    void paymentCheckNaoPagoNaoBaixa() {
        fila(item(1, "paid", pagamento("paid", "CR-7-0", "T1", 1500, 1500), "T1"));
        when(client.verificarPagamento(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new InfinitePayClient.PagamentoVerificado(true, false, 1500, 0, null, null));
        service.ciclo();
        assertEquals(InfinitePayEvento.Status.IGNORADO, unico().getStatus());
        verifyNoInteractions(recebimentoService);
    }

    @Test
    void tituloJaQuitadoVaiParaRevisaoComoDuplicidade() {
        titulo.setSituacao(SituacaoContaReceber.RECEBIDO);
        titulo.setValorRecebido(new BigDecimal("15.00"));
        link.setStatus(StatusLinkPagamento.ENCERRADO_TITULO_RECEBIDO);
        fila(item(1, "paid", pagamento("paid", "CR-7-0", "T1", 1500, 1500), "T1"));
        confirmaPago(1500, 1500);
        service.ciclo();
        assertEquals(InfinitePayEvento.Status.REVISAO, unico().getStatus());
        assertTrue(unico().getMotivo().contains("duplicidade"));
        verifyNoInteractions(recebimentoService);
    }

    @Test
    void linkDesconhecidoVaiParaRevisaoSemChamarAInfinitePay() {
        when(links.findIdByOrderNsu("OUTRO")).thenReturn(Optional.empty());
        fila(item(1, "paid", pagamento("paid", "OUTRO", "T9", 1500, 1500), "T9"));
        service.ciclo();
        assertEquals(InfinitePayEvento.Status.REVISAO, unico().getStatus());
        assertTrue(unico().getMotivo().contains("desconhecido"));
        verifyNoInteractions(client, recebimentoService);
    }

    @Test
    void estornoEPayloadInesperadoViramRevisaoSemBaixar() {
        fila(item(1, "refunded", pagamento("refunded", "CR-7-0", "T2", 1500, 1500), "T2"), item(2, "paid", "isto não é json", "T3"));
        service.ciclo();
        assertEquals(2, banco.size());
        assertTrue(banco.values().stream().allMatch(e -> e.getStatus() == InfinitePayEvento.Status.REVISAO));
        verifyNoInteractions(client, recebimentoService);
    }

    @Test
    void reenvioDoMesmoEventoNaoDuplicaNemBaixaDuasVezes() {
        String corpo = pagamento("paid", "CR-7-0", "T1", 1500, 1500);
        when(receptor.buscarFila(anyInt())).thenReturn(List.of(item(1, "paid", corpo, "T1")))
                .thenReturn(List.of(item(2, "paid", corpo, "T1")));
        confirmaPago(1500, 1500);
        service.ciclo();
        service.ciclo();
        assertEquals(1, banco.size());
        verify(recebimentoService, times(1)).receberAutomatico(any(), anyLong(), any(), any(), any(), any(), any(), any(), any());
        verify(receptor).ack(List.of(1L));
        verify(receptor).ack(List.of(2L)); // o duplicado também é confirmado, para sair da fila
    }

    @Test
    void falhaDeRedeNaBuscaNaoConfirmaNada() {
        when(receptor.buscarFila(anyInt())).thenThrow(new InfinitePayApiException("Receptor fora do ar."));
        BaixaAutomaticaService.Resumo r = service.ciclo();
        assertEquals(1, r.getErros());
        verify(receptor, never()).ack(anyList());
        assertTrue(banco.isEmpty());
    }

    @Test
    void falhaAoGuardarOEventoNaoConfirmaOItem() {
        fila(item(1, "paid", pagamento("paid", "CR-7-0", "T1", 1500, 1500), "T1"));
        doThrow(new IllegalStateException("banco fora")).when(eventos).saveAndFlush(any());
        BaixaAutomaticaService.Resumo r = service.ciclo();
        assertEquals(1, r.getErros());
        verify(receptor, never()).ack(anyList());
    }

    @Test
    void falhaDeRedeNoPaymentCheckGeraErroTemporarioETentaDepois() {
        when(receptor.buscarFila(anyInt())).thenReturn(List.of(item(1, "paid", pagamento("paid", "CR-7-0", "T1", 1500, 1500), "T1"))).thenReturn(List.of());
        doThrow(new InfinitePayApiException("fora do ar")).when(client).verificarPagamento(anyString(), anyString(), anyString(), anyString(), anyString());
        service.ciclo();
        assertEquals(InfinitePayEvento.Status.ERRO_TEMPORARIO, unico().getStatus());
        verify(receptor).ack(List.of(1L)); // já guardado no ERP: pode confirmar
        // volta o serviço: o próximo ciclo processa o pendente
        confirmaPago(1500, 1500);
        service.ciclo();
        assertEquals(InfinitePayEvento.Status.PROCESSADO, unico().getStatus());
    }

    @Test
    void comInterruptorDesligadoGuardaMasNaoProcessa() {
        when(automatico.baixaAutomaticaAtiva()).thenReturn(false);
        fila(item(1, "paid", pagamento("paid", "CR-7-0", "T1", 1500, 1500), "T1"));
        service.ciclo();
        assertEquals(InfinitePayEvento.Status.RECEBIDO, unico().getStatus());
        verifyNoInteractions(client, recebimentoService);
    }

    @Test
    void duasBuscasSimultaneasProcessamUmaSoVez() throws Exception {
        String corpo = pagamento("paid", "CR-7-0", "T1", 1500, 1500);
        AtomicInteger buscas = new AtomicInteger();
        // a primeira busca devolve o item (e demora); as demais devolvem a mesma fila já confirmada (vazia)
        when(receptor.buscarFila(anyInt())).thenAnswer(i -> {
            if (buscas.getAndIncrement() == 0) {
                Thread.sleep(200);
                return List.of(item(1, "paid", corpo, "T1"));
            }
            return List.of();
        });
        confirmaPago(1500, 1500);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        Future<?> a = pool.submit(() -> {
            largada.await();
            return service.sincronizar();
        });
        Future<?> b = pool.submit(() -> {
            largada.await();
            return service.sincronizar();
        });
        largada.countDown();
        a.get();
        b.get();
        pool.shutdown();
        verify(recebimentoService, times(1)).receberAutomatico(any(), anyLong(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(1, banco.size());
    }
}
