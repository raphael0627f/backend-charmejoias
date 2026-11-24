package com.manager.app.infinitepay;

import com.manager.app.financeiro.Banco;
import com.manager.app.financeiro.BancoRepository;
import com.manager.app.financeiro.ContaReceber;
import com.manager.app.financeiro.ContaReceberRecebimento;
import com.manager.app.financeiro.ContaReceberRecebimentoService;
import com.manager.app.financeiro.ContaReceberService;
import com.manager.app.financeiro.FormaPagamento;
import com.manager.app.financeiro.FormaPagamentoRepository;
import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.infinitepay.dto.RevisaoDTOs;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.LimiteExcedidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// ações da fila de revisão (aplicar, ignorar, reprocessar) e o limite do botão "Verificar pagamento", com repositórios simulados
class RevisaoInfinitePayServiceTest {

    private final InfinitePayEventoRepository eventos = mock(InfinitePayEventoRepository.class);
    private final ContaReceberLinkPagamentoRepository links = mock(ContaReceberLinkPagamentoRepository.class);
    private final ContaReceberService contaReceberService = mock(ContaReceberService.class);
    private final ContaReceberRecebimentoService recebimentoService = mock(ContaReceberRecebimentoService.class);
    private final FormaPagamentoRepository formaRepo = mock(FormaPagamentoRepository.class);
    private final BancoRepository bancoRepo = mock(BancoRepository.class);
    private final InfinitePayClient client = mock(InfinitePayClient.class);
    private final InfinitePayConfiguracaoService configuracao = mock(InfinitePayConfiguracaoService.class);
    private final InfinitePayAutomaticoService automatico = mock(InfinitePayAutomaticoService.class);
    private final TravaAdvisory trava = mock(TravaAdvisory.class);
    private final UsuarioAuditorAware auditor = mock(UsuarioAuditorAware.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);

    private InfinitePayEvento ev;
    private ContaReceber titulo;
    private ContaReceberLinkPagamento link;
    private Banco banco;
    private RevisaoInfinitePayService service;
    private VerificarPagamentoService verificar;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        when(tm.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        banco = new Banco();
        banco.setId(1L);
        banco.setNome("Banco");
        banco.setAtivo(true);
        titulo = new ContaReceber();
        titulo.setId(7L);
        titulo.setValor(new BigDecimal("15.00"));
        titulo.setValorRecebido(BigDecimal.ZERO);
        titulo.setSituacao(SituacaoContaReceber.ABERTO);
        link = new ContaReceberLinkPagamento();
        link.setId(5L);
        link.setContaReceber(titulo);
        link.setValor(new BigDecimal("15.00"));
        link.setModo(ModoLinkPagamento.AUTOMATICO);
        link.setStatus(StatusLinkPagamento.ATIVO);
        link.setBanco(banco);
        ev = new InfinitePayEvento();
        ev.setId(3L);
        ev.setStatus(InfinitePayEvento.Status.REVISAO);
        ev.setMotivoCodigo("FORMA_NAO_MAPEADA");
        ev.setTransactionNsu("T1");
        ev.setEvento("paid");
        ev.setLink(link);
        ev.setCriadoEm(LocalDateTime.now());
        ev.setPayload("{\"event\":\"paid\",\"invoice_slug\":\"s\",\"amount\":1500,\"paid_amount\":1510,\"capture_method\":\"credit_card\",\"transaction_nsu\":\"T1\",\"order_nsu\":\"O\"}");
        when(eventos.findById(3L)).thenReturn(Optional.of(ev));
        when(eventos.save(any())).thenAnswer(i -> i.getArgument(0));
        when(links.findById(5L)).thenReturn(Optional.of(link));
        when(contaReceberService.buscarComLock(7L)).thenReturn(titulo);
        when(configuracao.credenciais()).thenReturn(new InfinitePayConfiguracaoService.Credenciais("tag", "chave"));
        doReturn(new InfinitePayClient.PagamentoVerificado(true, true, 1500, 1510, 3, "credit_card")).when(client)
                .verificarPagamento(anyString(), anyString(), anyString(), anyString(), anyString());
        when(automatico.formaPorCaptura(anyString())).thenReturn(null); // forma não mapeada
        when(recebimentoService.receberAutomatico(any(), anyLong(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(i -> {
            ContaReceberRecebimento r = new ContaReceberRecebimento();
            r.setId(99L);
            return r;
        });
        usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(usuario));
        BaixaAutomaticaService baixa = new BaixaAutomaticaService(mock(ReceptorClient.class), automatico, configuracao, client, eventos, links, contaReceberService,
                recebimentoService, trava, JsonMapper.builder().build(), tm);
        service = new RevisaoInfinitePayService(eventos, links, contaReceberService, recebimentoService, formaRepo, bancoRepo, client, configuracao, automatico, trava, baixa,
                auditor, JsonMapper.builder().build(), tm);
        verificar = new VerificarPagamentoService(baixa, automatico, contaReceberService, links, eventos, auditor, tm);
    }

    private FormaPagamento forma() {
        FormaPagamento f = new FormaPagamento();
        f.setId(11L);
        f.setNome("Cartão");
        return f;
    }

    @Test
    void ignorarExigeMotivoEResolve() {
        assertThrows(ItemInvalidoException.class, () -> service.ignorar(3L, new RevisaoDTOs.IgnorarRequest("ok")));
        assertThrows(ItemInvalidoException.class, () -> service.ignorar(3L, null));
        RevisaoDTOs.Detalhe d = service.ignorar(3L, new RevisaoDTOs.IgnorarRequest("Cliente pagou em dinheiro"));
        assertEquals("RESOLVIDO_IGNORADO", d.status());
        assertEquals(InfinitePayEvento.Status.IGNORADO, ev.getStatus());
        assertEquals(usuario, ev.getResolvidoPor());
        assertTrue(d.acoesPermitidas().isEmpty());
        ObjetoEmUsoException jaResolvida = assertThrows(ObjetoEmUsoException.class, () -> service.ignorar(3L, new RevisaoDTOs.IgnorarRequest("De novo, por favor")));
        assertEquals("Esta revisão já foi resolvida.", jaResolvida.getMessage());
    }

    @Test
    void detalheOfereceAcoesEExigeBancoFormaNoMotivoCerto() {
        RevisaoDTOs.Detalhe d = service.detalhe(3L);
        assertTrue(d.exigeBancoForma());
        assertTrue(d.acoesPermitidas().containsAll(java.util.List.of("APLICAR", "REPROCESSAR", "IGNORAR")));
        assertNotNull(d.payloadBruto());
        assertEquals(7L, d.titulo().id());
        assertEquals(new BigDecimal("15.00"), d.titulo().saldoEmAberto());
        ev.setMotivoCodigo("DUPLICIDADE");
        d = service.detalhe(3L);
        assertFalse(d.exigeBancoForma());
        assertEquals(java.util.List.of("REPROCESSAR", "IGNORAR"), d.acoesPermitidas()); // duplicidade nunca tem "Aplicar"
    }

    @Test
    void aplicarSemFormaMapeadaPedeBancoEFormaComMensagemFormal() {
        ItemInvalidoException e = assertThrows(ItemInvalidoException.class, () -> service.aplicar(3L, null));
        assertTrue(e.getMessage().contains("Informe banco e forma de pagamento"));
        verify(recebimentoService, never()).receberAutomatico(any(), anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aplicarComFormaInformadaBaixaPeloValorDoLinkComTaxa() {
        when(formaRepo.findById(11L)).thenReturn(Optional.of(forma()));
        service.aplicar(3L, new RevisaoDTOs.AplicarRequest(null, 11L, null));
        verify(recebimentoService).receberAutomatico(eq(titulo), eq(1L), any(), any(), eq(new BigDecimal("15.00")), eq(new BigDecimal("0.10")), anyString(), eq(link), eq("T1"));
        assertEquals(InfinitePayEvento.Status.PROCESSADO, ev.getStatus());
        assertEquals(99L, ev.getRecebimentoId());
        assertEquals(usuario, ev.getResolvidoPor());
        assertEquals("RESOLVIDO_APLICADO", RevisaoInfinitePayService.statusExibicao(ev));
        assertThrows(ObjetoEmUsoException.class, () -> service.aplicar(3L, new RevisaoDTOs.AplicarRequest(null, 11L, null)));
    }

    @Test
    void aplicarComBancoDesativadoExigeOutroBanco() {
        banco.setAtivo(false);
        when(automatico.formaPorCaptura(anyString())).thenReturn(forma());
        ev.setMotivoCodigo("BANCO_DESATIVADO");
        ItemInvalidoException e = assertThrows(ItemInvalidoException.class, () -> service.aplicar(3L, null));
        assertTrue(e.getMessage().contains("Informe outro banco"));
    }

    @Test
    void valorMenorExigeConfirmacaoEBaixaSoOQueFoiPago() {
        ev.setMotivoCodigo("VALOR_MENOR");
        when(automatico.formaPorCaptura(anyString())).thenReturn(forma());
        doReturn(new InfinitePayClient.PagamentoVerificado(true, true, 1500, 1400, 1, "pix")).when(client)
                .verificarPagamento(anyString(), anyString(), anyString(), anyString(), anyString());
        assertThrows(ItemInvalidoException.class, () -> service.aplicar(3L, new RevisaoDTOs.AplicarRequest(null, null, null)));
        service.aplicar(3L, new RevisaoDTOs.AplicarRequest(null, null, true));
        verify(recebimentoService).receberAutomatico(any(), anyLong(), any(), any(), eq(new BigDecimal("14.00")), eq(BigDecimal.ZERO), anyString(), any(), any());
    }

    @Test
    void duplicidadeNaoPodeSerAplicada() {
        ev.setMotivoCodigo("DUPLICIDADE");
        assertThrows(ItemInvalidoException.class, () -> service.aplicar(3L, new RevisaoDTOs.AplicarRequest(null, 11L, null)));
        verifyNoInteractions(client);
    }

    @Test
    void naoAplicaSeAInfinitePayNaoConfirmar() {
        when(automatico.formaPorCaptura(anyString())).thenReturn(forma());
        doReturn(new InfinitePayClient.PagamentoVerificado(true, false, 1500, 0, null, null)).when(client)
                .verificarPagamento(anyString(), anyString(), anyString(), anyString(), anyString());
        assertThrows(ItemInvalidoException.class, () -> service.aplicar(3L, null));
        verify(recebimentoService, never()).receberAutomatico(any(), anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void verificarPagamentoLimitaUmaChamadaACada10SegundosPorUsuario() {
        when(contaReceberService.buscar(7L)).thenReturn(titulo);
        when(links.findByContaReceberIdAndStatus(7L, StatusLinkPagamento.ATIVO)).thenReturn(java.util.List.of(link));
        when(automatico.baixaAutomaticaAtiva()).thenReturn(false);
        // desligada: 409 na primeira, e a segunda chamada imediata já é barrada pelo limite (429)
        assertThrows(ObjetoEmUsoException.class, () -> verificar.verificar(7L));
        assertThrows(LimiteExcedidoException.class, () -> verificar.verificar(7L));
    }

    @Test
    void verificarPagamentoSemLinkAutomaticoAtivoEhInvalido() {
        when(contaReceberService.buscar(7L)).thenReturn(titulo);
        when(links.findByContaReceberIdAndStatus(7L, StatusLinkPagamento.ATIVO)).thenReturn(java.util.List.of());
        assertThrows(ItemInvalidoException.class, () -> verificar.verificar(7L));
    }

    // ---------- 6.6: reprocessar nunca move sozinho para ignorado ----------

    private void prepararReprocessar() {
        when(links.findIdByOrderNsu("O")).thenReturn(Optional.of(5L));
        ev.setMotivo("Meio de pagamento sem forma configurada.");
        ev.setTentativas(2);
    }

    @Test
    void reprocessarExigeConfirmacaoNoCorpo() {
        assertThrows(ItemInvalidoException.class, () -> service.reprocessar(3L, null));
        assertThrows(ItemInvalidoException.class, () -> service.reprocessar(3L, new RevisaoDTOs.ReprocessarRequest(false)));
        assertThrows(ItemInvalidoException.class, () -> service.reprocessar(3L, new RevisaoDTOs.ReprocessarRequest(null)));
        verifyNoInteractions(client);
    }

    @Test
    void reprocessarSemConfirmacaoDaInfinitePayMantemEmRevisaoComAvisoFormal() {
        prepararReprocessar();
        doReturn(new InfinitePayClient.PagamentoVerificado(true, false, 1500, 0, null, null)).when(client)
                .verificarPagamento(anyString(), anyString(), anyString(), anyString(), anyString());
        RevisaoDTOs.Detalhe d = service.reprocessar(3L, new RevisaoDTOs.ReprocessarRequest(true));
        assertEquals(InfinitePayEvento.Status.REVISAO, ev.getStatus());
        assertEquals("FORMA_NAO_MAPEADA", ev.getMotivoCodigo());
        assertEquals("Meio de pagamento sem forma configurada.", ev.getMotivo());
        assertEquals(2, ev.getTentativas());
        assertNull(ev.getResolvidoEm());
        assertEquals(RevisaoInfinitePayService.AVISO_NAO_CONFIRMADO, d.aviso());
        assertFalse(d.aviso().toLowerCase().contains("payment_check"));
        assertEquals("REVISAO", d.status());
    }

    @Test
    void reprocessarComFalhaNaConsultaMantemEmRevisaoComAvisoFormal() {
        prepararReprocessar();
        doThrow(new InfinitePayApiException("falhou", null)).when(client).verificarPagamento(anyString(), anyString(), anyString(), anyString(), anyString());
        RevisaoDTOs.Detalhe d = service.reprocessar(3L, new RevisaoDTOs.ReprocessarRequest(true));
        assertEquals(InfinitePayEvento.Status.REVISAO, ev.getStatus());
        assertEquals("FORMA_NAO_MAPEADA", ev.getMotivoCodigo());
        assertEquals(RevisaoInfinitePayService.AVISO_CONSULTA_FALHOU, d.aviso());
    }

    @Test
    void listagemValidaOrdenacaoDatasEMotivo() {
        var pagina = org.springframework.data.domain.PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("campoInexistente"));
        assertThrows(ItemInvalidoException.class, () -> RevisaoInfinitePayService.validarListagem(null, null, null, pagina));
        assertThrows(ItemInvalidoException.class, () -> RevisaoInfinitePayService.validarListagem(null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("link.contaReceber.cliente.nome"))));
        assertDoesNotThrow(() -> RevisaoInfinitePayService.validarListagem(null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("criadoEm").descending())));
        java.time.LocalDate hoje = java.time.LocalDate.now();
        ItemInvalidoException datas = assertThrows(ItemInvalidoException.class, () -> RevisaoInfinitePayService.validarListagem(null, hoje, hoje.minusDays(1), null));
        assertEquals("A data inicial não pode ser posterior à data final.", datas.getMessage());
        ItemInvalidoException motivo = assertThrows(ItemInvalidoException.class, () -> RevisaoInfinitePayService.validarListagem("XPTO", null, null, null));
        assertEquals("Motivo de revisão inválido.", motivo.getMessage());
        assertDoesNotThrow(() -> RevisaoInfinitePayService.validarListagem("valor_menor", null, null, null));
    }

    @Test
    void alertaBaixaDesligadaSoComIntegracaoEAlgoAguardando() {
        assertTrue(VerificarPagamentoService.deveAlertarBaixaDesligada(false, true, 1, 0));
        assertTrue(VerificarPagamentoService.deveAlertarBaixaDesligada(false, true, 0, 3));
        assertFalse(VerificarPagamentoService.deveAlertarBaixaDesligada(false, true, 0, 0));
        assertFalse(VerificarPagamentoService.deveAlertarBaixaDesligada(true, true, 5, 5));
        assertFalse(VerificarPagamentoService.deveAlertarBaixaDesligada(false, false, 5, 5));
        assertEquals("A baixa automática está desligada. Os pagamentos recebidos pelos links automáticos permanecem aguardando e serão baixados somente após a ativação.",
                VerificarPagamentoService.MSG_BAIXA_DESLIGADA);
    }

    @Test
    void estadoDoReceptorIndisponivelViraCamposNulosSemErro() {
        assertEquals(ReceptorEstado.INDISPONIVEL, ReceptorEstado.de(null));
        assertFalse(ReceptorEstado.INDISPONIVEL.disponivel());
        var s = new com.manager.app.infinitepay.dto.InfinitePayDTOs.ReceptorSaude(true, 2L, "2026-01-01T00:00:00Z", "1.2.0", 7L, 5L, "2025-12-01T00:00:00Z", 90);
        ReceptorEstado e = ReceptorEstado.de(s);
        assertTrue(e.disponivel());
        assertEquals(7L, e.totalRetidos());
        assertEquals(2L, e.pendentes());
        assertEquals(5L, e.confirmadosRetidos());
        assertEquals(90, e.retencaoDias());
        // Worker antigo (sem as contagens novas): disponível, campos novos nulos
        ReceptorEstado antigo = ReceptorEstado.de(new com.manager.app.infinitepay.dto.InfinitePayDTOs.ReceptorSaude(true, 2L, null, "1.1.0", null, null, null, null));
        assertTrue(antigo.disponivel());
        assertNull(antigo.totalRetidos());
        assertFalse(ReceptorEstado.de(new com.manager.app.infinitepay.dto.InfinitePayDTOs.ReceptorSaude(false, null, null, null, null, null, null, null)).disponivel());
    }

    @Test
    void reprocessarComPagamentoConfirmadoMasSemFormaNaoDizQueBaixou() {
        prepararReprocessar();
        RevisaoDTOs.Detalhe d = service.reprocessar(3L, new RevisaoDTOs.ReprocessarRequest(true));
        assertEquals("REVISAO", d.status());
        assertEquals("FORMA_NAO_MAPEADA", d.motivoCodigo());
        assertFalse(d.baixado());
        assertNull(d.recebimentoId());
        assertEquals("O pagamento foi confirmado, mas o meio de pagamento não possui forma configurada. Utilize Aplicar informando banco e forma.", d.aviso());
        verify(recebimentoService, never()).receberAutomatico(any(), anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void avisoDePagamentoConfirmadoSemBaixaPorMotivo() {
        assertTrue(RevisaoInfinitePayService.avisoConfirmadoSemBaixa("BANCO_DESATIVADO", "x").contains("banco de destino do link está desativado"));
        assertTrue(RevisaoInfinitePayService.avisoConfirmadoSemBaixa("VALOR_MENOR", "Valor pago menor que o cobrado").startsWith("O pagamento foi confirmado, mas não foi baixado"));
    }
}
