package com.manager.app.infinitepay;

import com.manager.app.email.SegredoCriptografiaService;
import com.manager.app.financeiro.Banco;
import com.manager.app.financeiro.BancoRepository;
import com.manager.app.financeiro.FormaPagamento;
import com.manager.app.financeiro.FormaPagamentoRepository;
import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// configuração da baixa automática com repositórios simulados: nada de rede nem de banco
class InfinitePayAutomaticoServiceTest {

    private static final String TOKEN = "tokenEntradaTokenEntradaTokenEntrada1234";
    private static final String SEGREDO = "segredoErpSegredoErpSegredoErpSegredo9876";

    private final InfinitePayConfiguracaoRepository repo = mock(InfinitePayConfiguracaoRepository.class);
    private final BancoRepository bancoRepo = mock(BancoRepository.class);
    private final FormaPagamentoRepository formaRepo = mock(FormaPagamentoRepository.class);
    private final SegredoCriptografiaService criptografia = new SegredoCriptografiaService("segredo-de-teste-para-criptografia", "");
    private InfinitePayConfiguracao config;
    private InfinitePayAutomaticoService service;

    @BeforeEach
    void preparar() {
        config = new InfinitePayConfiguracao();
        config.setHandle("minha-tag");
        config.setApiKeyCriptografada(criptografia.criptografar("inchk_teste_1111"));
        when(repo.findById(InfinitePayConfiguracao.ID_UNICO)).thenAnswer(i -> Optional.of(config));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new InfinitePayAutomaticoService(repo, criptografia, mock(UsuarioResumoMapper.class), bancoRepo, formaRepo, false);
        banco(1, true);
        forma(10, "PIX", true);
        forma(11, "Cartão de crédito", true);
    }

    private void banco(long id, boolean ativo) {
        Banco b = new Banco();
        b.setId(id);
        b.setNome("Banco " + id);
        b.setAtivo(ativo);
        when(bancoRepo.findById(id)).thenReturn(Optional.of(b));
    }

    private void forma(long id, String nome, boolean ativo) {
        FormaPagamento f = new FormaPagamento();
        f.setId(id);
        f.setNome(nome);
        f.setAtivo(ativo);
        when(formaRepo.findById(id)).thenReturn(Optional.of(f));
    }

    private InfinitePayDTOs.AutomaticoRequest req(Boolean ativa, String url, String token, String segredo, Integer intervalo, Long banco, Long pix, Long cartao) {
        return new InfinitePayDTOs.AutomaticoRequest(ativa, null, url, token, segredo, intervalo, banco, pix, cartao);
    }

    private void configurarCompleto() {
        service.salvar(req(null, "https://pagamentos.charmejoias.com.br", TOKEN, SEGREDO, null, 1L, 10L, 11L));
    }

    @Test
    void segredosFicamCriptografadosENuncaSaemNaResposta() {
        configurarCompleto();
        assertNotNull(config.getReceptorTokenEntradaCriptografado());
        assertFalse(config.getReceptorTokenEntradaCriptografado().contains(TOKEN));
        assertFalse(config.getReceptorSegredoErpCriptografado().contains(SEGREDO));
        InfinitePayDTOs.AutomaticoResponse r = service.obter();
        assertTrue(r.tokenEntradaConfigurado());
        assertEquals("****1234", r.tokenEntradaFinal());
        assertEquals("****9876", r.segredoErpFinal());
        assertFalse(r.toString().contains(TOKEN));
        assertFalse(r.toString().contains(SEGREDO));
        assertTrue(r.prontoParaAutomatico());
        assertTrue(r.pendencias().isEmpty());
        assertEquals(2, r.intervaloFilaMinutos());
        assertFalse(r.baixaAutomaticaAtiva());
    }

    @Test
    void segredoVazioMantemOSalvoEOutrosCamposNaoMudam() {
        configurarCompleto();
        String antes = config.getReceptorTokenEntradaCriptografado();
        service.salvar(req(null, null, "  ", null, 5, null, null, null));
        assertEquals(antes, config.getReceptorTokenEntradaCriptografado());
        assertEquals(5, config.getIntervaloFilaMinutos());
        assertEquals("https://pagamentos.charmejoias.com.br", config.getReceptorUrl());
    }

    @Test
    void validaUrlSegredosIntervaloBancoEForma() {
        assertThrows(ItemInvalidoException.class, () -> service.salvar(req(null, "https://x.workers.dev", null, null, null, null, null, null)));
        assertThrows(ItemInvalidoException.class, () -> service.salvar(req(null, null, "curto", null, null, null, null, null)));
        assertThrows(ItemInvalidoException.class, () -> service.salvar(req(null, null, null, null, 0, null, null, null)));
        banco(2, false);
        assertThrows(ItemInvalidoException.class, () -> service.salvar(req(null, null, null, null, null, 2L, null, null)));
        assertThrows(ItemInvalidoException.class, () -> service.salvar(req(null, null, null, null, null, 99L, null, null)));
        forma(12, "Antiga", false);
        assertThrows(ItemInvalidoException.class, () -> service.salvar(req(null, null, null, null, null, null, 12L, null)));
        assertNull(config.getReceptorUrl());
    }

    @Test
    void segredoDoErpDiferenteDoTokenDeEntrada() {
        assertThrows(ItemInvalidoException.class, () -> service.salvar(req(null, null, TOKEN, TOKEN, null, null, null, null)));
    }

    @Test
    void interruptorSoLigaComConfiguracaoCompletaEDesligarSempreFunciona() {
        ObjetoEmUsoException e = assertThrows(ObjetoEmUsoException.class, () -> service.salvar(req(true, null, null, null, null, null, null, null)));
        assertTrue(e.getMessage().contains("URL do receptor"));
        assertFalse(config.isBaixaAutomaticaAtiva());
        configurarCompleto();
        assertTrue(service.salvar(req(true, null, null, null, null, null, null, null)).baixaAutomaticaAtiva());
        assertTrue(service.baixaAutomaticaAtiva());
        // parada de emergência
        assertFalse(service.salvar(req(false, null, null, null, null, null, null, null)).baixaAutomaticaAtiva());
    }

    @Test
    void ligadaNaoPermiteRemoverPecaEssencial() {
        configurarCompleto();
        service.salvar(req(true, null, null, null, null, null, null, null));
        assertThrows(ObjetoEmUsoException.class, () -> service.salvar(req(null, null, null, null, null, null, 0L, null)));
    }

    @Test
    void webhookSoSaiComTudoConfiguradoEBaixaLigada() {
        configurarCompleto();
        ObjetoEmUsoException desligada = assertThrows(ObjetoEmUsoException.class, () -> service.webhookParaLink());
        assertTrue(desligada.getMessage().contains("desligada"));
        service.salvar(req(true, null, null, null, null, null, null, null));
        Map<String, Object> w = service.webhookParaLink();
        assertEquals("https://pagamentos.charmejoias.com.br/w/" + TOKEN, w.get("url"));
        assertFalse(String.valueOf(w.get("url")).contains("workers.dev"));
    }

    @Test
    void webhookRecusaUrlSalvaInvalidaNaGeracao() {
        configurarCompleto();
        service.salvar(req(true, null, null, null, null, null, null, null));
        config.setReceptorUrl("https://charme.conta.workers.dev");
        assertThrows(ItemInvalidoException.class, () -> service.webhookParaLink());
    }

    @Test
    void formaPorMeioDeCapturaEDesconhecidoSemForma() {
        configurarCompleto();
        assertEquals("PIX", service.formaPorCaptura("pix").getNome());
        assertEquals("PIX", service.formaPorCaptura(" PIX ").getNome());
        assertEquals("Cartão de crédito", service.formaPorCaptura("credit_card").getNome());
        assertNull(service.formaPorCaptura("boleto"));
        assertNull(service.formaPorCaptura(null));
    }

    @Test
    void receptorSemConfiguracaoRecusaEComConfiguracaoDevolveSegredoLegivel() {
        assertThrows(ObjetoEmUsoException.class, () -> service.receptor());
        configurarCompleto();
        InfinitePayAutomaticoService.Receptor r = service.receptor();
        assertEquals("https://pagamentos.charmejoias.com.br", r.url());
        assertEquals(SEGREDO, r.segredoErp());
    }

    @Test
    void semInfiniteTagEChaveHaPendencia() {
        config.setApiKeyCriptografada(null);
        service.salvar(req(null, "https://pagamentos.charmejoias.com.br", TOKEN, SEGREDO, null, null, 10L, 11L));
        InfinitePayDTOs.AutomaticoResponse r = service.obter();
        assertFalse(r.prontoParaAutomatico());
        assertTrue(r.pendencias().get(0).contains("InfiniteTag"));
    }
}
