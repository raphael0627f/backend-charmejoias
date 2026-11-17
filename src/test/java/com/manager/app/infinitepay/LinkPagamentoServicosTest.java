package com.manager.app.infinitepay;

import com.manager.app.cliente.Cliente;
import com.manager.app.email.ConfiguracaoEmailService;
import com.manager.app.email.EmailTemplateService;
import com.manager.app.email.SegredoCriptografiaService;
import com.manager.app.financeiro.ContaReceber;
import com.manager.app.financeiro.ContaReceberEvento;
import com.manager.app.financeiro.ContaReceberEventoRepository;
import com.manager.app.financeiro.ContaReceberRecebimento;
import com.manager.app.financeiro.ContaReceberRecebimentoRepository;
import com.manager.app.financeiro.ContaReceberService;
import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// regras de serviço com repositórios e cliente HTTP simulados (Mockito): nada de rede, banco ou e-mail
class LinkPagamentoServicosTest {

    private static final String CHAVE = "inchk_chave_de_teste_9876";

    private final ContaReceberLinkPagamentoRepository repo = mock(ContaReceberLinkPagamentoRepository.class);
    private final ContaReceberEventoRepository eventos = mock(ContaReceberEventoRepository.class);
    private final UsuarioAuditorAware auditor = mock(UsuarioAuditorAware.class);
    private final InfinitePayConfiguracaoRepository configRepo = mock(InfinitePayConfiguracaoRepository.class);
    private final ContaReceberService contaReceberService = mock(ContaReceberService.class);
    private final ContaReceberRecebimentoRepository recebimentoRepo = mock(ContaReceberRecebimentoRepository.class);
    private final InfinitePayClient client = mock(InfinitePayClient.class);
    private final ConfiguracaoEmailService emailService = mock(ConfiguracaoEmailService.class);
    private final EmailTemplateService templateService = mock(EmailTemplateService.class);
    private final UsuarioResumoMapper usuarioMapper = mock(UsuarioResumoMapper.class);
    private final SegredoCriptografiaService criptografia = new SegredoCriptografiaService("segredo-de-teste-para-criptografia", "");

    private LinkPagamentoCicloVida cicloVida;
    private InfinitePayConfiguracaoService configService;
    private LinkPagamentoService service;
    private InfinitePayConfiguracao config;
    private final List<ContaReceberLinkPagamento> salvos = new ArrayList<>();

    @BeforeEach
    void preparar() {
        when(auditor.getCurrentAuditor()).thenReturn(Optional.empty());
        cicloVida = new LinkPagamentoCicloVida(repo, eventos, auditor);
        config = new InfinitePayConfiguracao();
        when(configRepo.findById(InfinitePayConfiguracao.ID_UNICO)).thenAnswer(i -> Optional.of(config));
        when(configRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        configService = new InfinitePayConfiguracaoService(configRepo, criptografia, usuarioMapper, false);
        service = new LinkPagamentoService(contaReceberService, repo, recebimentoRepo, configService, client, cicloVida, auditor, usuarioMapper, emailService, templateService);
        when(repo.save(any())).thenAnswer(i -> {
            ContaReceberLinkPagamento l = i.getArgument(0);
            if (l.getId() == null) l.setId((long) (salvos.size() + 1));
            if (!salvos.contains(l)) salvos.add(l);
            return l;
        });
    }

    private void configurar() {
        configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest("$minha-tag", CHAVE, null, null, null, null, null, null, null, null, null, null));
    }

    private ContaReceber titulo(long id, SituacaoContaReceber situacao, String valor, String recebido) {
        ContaReceber t = new ContaReceber();
        t.setId(id);
        t.setNumero(id);
        t.setSituacao(situacao);
        t.setValor(new BigDecimal(valor));
        t.setValorRecebido(new BigDecimal(recebido));
        t.setDescricao("Título " + id);
        Cliente c = new Cliente();
        c.setNome("Maria");
        c.setEmail("maria@exemplo.com");
        c.setCelular("(24) 99999-8888");
        t.setCliente(c);
        when(contaReceberService.buscarComLock(id)).thenReturn(t);
        when(contaReceberService.buscar(id)).thenReturn(t);
        return t;
    }

    private ContaReceberLinkPagamento link(long id, ContaReceber t, StatusLinkPagamento status) {
        ContaReceberLinkPagamento l = new ContaReceberLinkPagamento();
        l.setId(id);
        l.setContaReceber(t);
        l.setStatus(status);
        l.setValor(new BigDecimal("10.00"));
        l.setDescricao("x");
        return l;
    }

    // ---------- configuração ----------

    @Test
    void chaveNuncaEDevolvidaEFicaCriptografada() {
        configurar();
        assertNotNull(config.getApiKeyCriptografada());
        assertFalse(config.getApiKeyCriptografada().contains(CHAVE));
        assertEquals("minha-tag", config.getHandle());
        InfinitePayDTOs.ConfiguracaoResponse r = configService.obter();
        assertTrue(r.chaveConfigurada());
        assertEquals("****9876", r.chaveFinal());
        assertFalse(r.toString().contains(CHAVE));
        assertFalse(r.envioEmailHabilitado());
        // chave vazia mantém a anterior; removerChave apaga
        configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest(null, "  ", null, 10, null, null, null, null, null, null, null, null));
        assertTrue(configService.obter().chaveConfigurada());
        assertEquals(10, config.getValidadeDias());
        configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest(null, null, true, null, null, null, null, null, null, null, null, null));
        assertFalse(configService.obter().chaveConfigurada());
        assertNull(configService.obter().chaveFinal());
    }

    @Test
    void configuracaoValidaPadroes() {
        assertThrows(ItemInvalidoException.class, () -> configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest(null, null, null, 366, null, null, null, null, null, null, null, null)));
        assertThrows(ItemInvalidoException.class, () -> configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest(null, null, null, null, null, 13, null, null, null, null, null, null)));
        assertThrows(ItemInvalidoException.class, () -> configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest(null, null, null, null, null, null, null, new BigDecimal("100"), null, null, null, null)));
        assertThrows(ItemInvalidoException.class, () -> configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest("tag invalida", null, null, null, null, null, null, null, null, null, null, null)));
        assertThrows(ItemInvalidoException.class, () -> configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest(null, null, null, null, null, null, false, new BigDecimal("5"), null, null, null, null)));
    }

    @Test
    void padroesNaoExpoemHandleNemChaveNemTextos() {
        InfinitePayDTOs.PadroesResponse sem = configService.padroes();
        assertFalse(sem.configurada());
        configurar();
        InfinitePayDTOs.PadroesResponse p = configService.padroes();
        assertTrue(p.configurada());
        assertEquals(7, p.validadeDias());
        assertTrue(p.pagamentoUnico());
        assertTrue(p.pixHabilitado());
        assertFalse(p.exigeEnderecoEntrega());
        assertFalse(p.envioEmailHabilitado());
        assertFalse(p.toString().contains(CHAVE));
        assertFalse(p.toString().contains("minha-tag"));
    }

    @Test
    void semConfiguracaoGeracaoRecusadaComMensagemFormal() {
        titulo(1, SituacaoContaReceber.ABERTO, "100.00", "0");
        ObjetoEmUsoException e = assertThrows(ObjetoEmUsoException.class, () -> service.gerar(1L, null));
        assertEquals("Configure a InfinitePay em Sistema antes de gerar links.", e.getMessage());
        verifyNoInteractions(client);
        // só handle, sem chave: ainda não configurada
        configService.salvar(new InfinitePayDTOs.ConfiguracaoRequest("tag", null, null, null, null, null, null, null, null, null, null, null));
        assertThrows(ObjetoEmUsoException.class, () -> service.gerar(1L, null));
    }

    // ---------- geração ----------

    @Test
    void geraLinkComPadroesESaldoEClienteDoCadastro() {
        configurar();
        titulo(5, SituacaoContaReceber.RECEBIDO_PARCIAL, "300.00", "100.00");
        when(client.criarLink(eq(CHAVE), anyMap())).thenReturn(new InfinitePayClient.LinkCriado("https://checkout.simulado/x", "{}"));
        LinkPagamentoService.Resultado r = service.gerar(5L, null);
        assertFalse(r.reaproveitado());
        InfinitePayDTOs.LinkResponse l = r.link();
        assertEquals(new BigDecimal("200.00"), l.valor());
        assertEquals("Título 5", l.descricao());
        assertEquals("ATIVO", l.status());
        assertNull(l.validade());
        assertTrue(l.pixHabilitado());
        assertEquals("+5524999998888", l.cliente().telefone());
        assertTrue(l.orderNsu().startsWith("CR-5-"));
        assertTrue(l.textoWhatsapp().contains("https://checkout.simulado/x"));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> pedido = ArgumentCaptor.forClass(Map.class);
        verify(client).criarLink(eq(CHAVE), pedido.capture());
        assertEquals("minha-tag", pedido.getValue().get("handle"));
        ArgumentCaptor<ContaReceberEvento> evento = ArgumentCaptor.forClass(ContaReceberEvento.class);
        verify(eventos).save(evento.capture());
        assertEquals("LINK_PAGAMENTO_GERADO", evento.getValue().getEvento());
        assertFalse(evento.getValue().getDetalhe().contains("https://"), "o histórico não registra a URL");
    }

    @Test
    void somenteTituloAbertoOuParcial() {
        configurar();
        for (SituacaoContaReceber s : List.of(SituacaoContaReceber.CANCELADO, SituacaoContaReceber.RECEBIDO)) {
            titulo(2, s, "100.00", "0");
            assertThrows(ItemInvalidoException.class, () -> service.gerar(2L, null), s.name());
        }
        verifyNoInteractions(client);
    }

    @Test
    void valorValidadeETaxaValidadosAntesDeChamarAInfinitePay() {
        configurar();
        titulo(3, SituacaoContaReceber.ABERTO, "100.00", "0");
        assertThrows(ItemInvalidoException.class, () -> service.gerar(3L, req(new BigDecimal("100.01"), null, null, null)));
        assertThrows(ItemInvalidoException.class, () -> service.gerar(3L, req(new BigDecimal("0.99"), null, null, null)));
        assertThrows(ItemInvalidoException.class, () -> service.gerar(3L, req(null, LocalDate.now(), null, null)));
        assertThrows(ItemInvalidoException.class, () -> service.gerar(3L, req(null, LocalDate.now().plusYears(1).plusDays(2), null, null)));
        assertThrows(ItemInvalidoException.class, () -> service.gerar(3L, req(null, null, 13, null)));
        assertThrows(ItemInvalidoException.class, () -> service.gerar(3L, req(null, null, null, new BigDecimal("100"))));
        verifyNoInteractions(client);
    }

    private InfinitePayDTOs.LinkRequest req(BigDecimal valor, LocalDate validade, Integer taxa, BigDecimal descontoPix) {
        return new InfinitePayDTOs.LinkRequest(null, valor, validade, null, taxa, null, descontoPix, null, null, null);
    }

    @Test
    void duploCliqueDevolveOLinkExistenteSemChamarAInfinitePay() {
        configurar();
        ContaReceber t = titulo(7, SituacaoContaReceber.ABERTO, "100.00", "0");
        when(client.criarLink(eq(CHAVE), anyMap())).thenReturn(new InfinitePayClient.LinkCriado("https://checkout.simulado/y", "{}"));
        LinkPagamentoService.Resultado primeiro = service.gerar(7L, null);
        assertFalse(primeiro.reaproveitado());
        ContaReceberLinkPagamento criado = salvos.get(0);
        when(repo.findByContaReceberIdAndStatusAndCriadoEmAfter(eq(7L), eq(StatusLinkPagamento.ATIVO), any())).thenReturn(List.of(criado));
        LinkPagamentoService.Resultado segundo = service.gerar(7L, null);
        assertTrue(segundo.reaproveitado());
        assertTrue(segundo.link().reaproveitado());
        assertEquals(primeiro.link().id(), segundo.link().id());
        verify(client, times(1)).criarLink(anyString(), anyMap());
        // dados diferentes: novo registro com order_nsu próprio
        LinkPagamentoService.Resultado outro = service.gerar(7L, req(new BigDecimal("50.00"), null, null, null));
        assertFalse(outro.reaproveitado());
        verify(client, times(2)).criarLink(anyString(), anyMap());
        assertEquals(2, salvos.size());
        assertNotEquals(salvos.get(0).getOrderNsu(), salvos.get(1).getOrderNsu());
        assertSame(t, salvos.get(0).getContaReceber());
    }

    // ---------- ciclo de vida ----------

    @Test
    void quitacaoEncerraSomenteQuandoRecebido() {
        ContaReceber t = titulo(10, SituacaoContaReceber.RECEBIDO_PARCIAL, "100.00", "40.00");
        ContaReceberLinkPagamento ativo = link(1, t, StatusLinkPagamento.ATIVO);
        when(repo.findByContaReceberIdAndStatus(10L, StatusLinkPagamento.ATIVO)).thenReturn(List.of(ativo));
        cicloVida.aoQuitar(t);
        assertEquals(StatusLinkPagamento.ATIVO, ativo.getStatus());
        t.setSituacao(SituacaoContaReceber.RECEBIDO);
        cicloVida.aoQuitar(t);
        assertEquals(StatusLinkPagamento.ENCERRADO_TITULO_RECEBIDO, ativo.getStatus());
    }

    @Test
    void cancelamentoMarcaLinksEAvisaSoQuandoHaviaAtivo() {
        ContaReceber t = titulo(11, SituacaoContaReceber.CANCELADO, "100.00", "0");
        assertFalse(cicloVida.aoCancelar(t));
        ContaReceberLinkPagamento ativo = link(2, t, StatusLinkPagamento.ATIVO);
        when(repo.findByContaReceberIdAndStatus(11L, StatusLinkPagamento.ATIVO)).thenReturn(List.of(ativo));
        assertTrue(cicloVida.aoCancelar(t));
        assertEquals(StatusLinkPagamento.TITULO_CANCELADO, ativo.getStatus());
        assertTrue(LinkPagamentoCicloVida.AVISO_CANCELAMENTO.contains("ainda podem ser pagos"));
    }

    @Test
    void avisoDoLoteSoQuandoHaLinkAtivoComAQuantidade() {
        assertNull(LinkPagamentoCicloVida.avisoDoLote(0));
        assertEquals("Os links de pagamento já enviados de 1 título cancelado não podem ser cancelados na InfinitePay e ainda podem ser pagos.", LinkPagamentoCicloVida.avisoDoLote(1));
        assertEquals("Os links de pagamento já enviados de 3 títulos cancelados não podem ser cancelados na InfinitePay e ainda podem ser pagos.", LinkPagamentoCicloVida.avisoDoLote(3));
    }

    @Test
    void marcarPagoAvulsoERecusadoSempreComOrientacaoFormal() {
        ItemInvalidoException e = assertThrows(ItemInvalidoException.class, () -> service.marcarPago(1L, null));
        assertEquals("Não é possível marcar o link como pago sem registrar o recebimento. Registre o recebimento do título informando o link de pagamento utilizado.", e.getMessage());
        assertThrows(ItemInvalidoException.class, () -> service.marcarPago(1L, new InfinitePayDTOs.MarcarPagoRequest(52L, null)));
    }

    @Test
    void linkIdDoRecebimentoPrecisaSerDoTituloEEstarAtivo() {
        ContaReceber t = titulo(20, SituacaoContaReceber.ABERTO, "100.00", "0");
        ContaReceber outro = titulo(21, SituacaoContaReceber.ABERTO, "100.00", "0");
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ItemInvalidoException.class, () -> cicloVida.validarParaRecebimento(20L, 99L));
        ContaReceberLinkPagamento deOutro = link(1, outro, StatusLinkPagamento.ATIVO);
        when(repo.findById(1L)).thenReturn(Optional.of(deOutro));
        ItemInvalidoException e1 = assertThrows(ItemInvalidoException.class, () -> cicloVida.validarParaRecebimento(20L, 1L));
        assertTrue(e1.getMessage().contains("não pertence a este título"));
        ContaReceberLinkPagamento pago = link(2, t, StatusLinkPagamento.PAGO_MANUAL);
        when(repo.findById(2L)).thenReturn(Optional.of(pago));
        ItemInvalidoException e2 = assertThrows(ItemInvalidoException.class, () -> cicloVida.validarParaRecebimento(20L, 2L));
        assertTrue(e2.getMessage().contains("não está ativo"));
        ContaReceberLinkPagamento ok = link(3, t, StatusLinkPagamento.ATIVO);
        when(repo.findById(3L)).thenReturn(Optional.of(ok));
        assertSame(ok, cicloVida.validarParaRecebimento(20L, 3L));
        // baixa pelo link: PAGO_MANUAL com recebimento; ao quitar, nenhum outro estado do próprio link muda
        when(repo.findByContaReceberIdAndStatus(20L, StatusLinkPagamento.ATIVO)).thenReturn(List.of(ok));
        cicloVida.marcarPago(ok, 77L, "Recebimento registrado com o link.");
        assertEquals(StatusLinkPagamento.PAGO_MANUAL, ok.getStatus());
        assertEquals(77L, ok.getRecebimentoId());
        t.setSituacao(SituacaoContaReceber.RECEBIDO);
        when(repo.findByContaReceberIdAndStatus(20L, StatusLinkPagamento.ATIVO)).thenReturn(List.of());
        cicloVida.aoQuitar(t);
        assertEquals(StatusLinkPagamento.PAGO_MANUAL, ok.getStatus());
    }

    // ---------- e-mail desabilitado ----------

    @Test
    void envioDeEmailDesabilitadoRespondeConflitoSemTocarNoEmail() {
        ObjetoEmUsoException e = assertThrows(ObjetoEmUsoException.class,
                () -> service.enviarEmail(1L, new InfinitePayDTOs.EnviarEmailRequest(List.of("maria@exemplo.com"), "Assunto", "Mensagem")));
        assertEquals("O envio por e-mail está desabilitado no momento.", e.getMessage());
        verifyNoInteractions(emailService, templateService, repo);
        assertFalse(configService.envioEmailHabilitado());
        // com a flag ligada o serviço consulta o link antes de qualquer envio (aqui inexistente => 404, sem e-mail)
        InfinitePayConfiguracaoService ligado = new InfinitePayConfiguracaoService(configRepo, criptografia, usuarioMapper, true);
        LinkPagamentoService comEmail = new LinkPagamentoService(contaReceberService, repo, recebimentoRepo, ligado, client, cicloVida, auditor, usuarioMapper, emailService, templateService);
        when(repo.findContaReceberIdById(anyLong())).thenReturn(Optional.empty());
        assertThrows(com.manager.app.shared.exception.ObjetoNaoEncontradoException.class,
                () -> comEmail.enviarEmail(1L, new InfinitePayDTOs.EnviarEmailRequest(List.of("maria@exemplo.com"), null, null)));
        verifyNoInteractions(emailService);
    }
}
