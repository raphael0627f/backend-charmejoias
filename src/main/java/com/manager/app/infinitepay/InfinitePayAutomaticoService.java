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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// configuração da baixa automática (fase 3B): receptor na nuvem, segredos, interruptor, intervalo e mapeamento banco/forma.
// Os segredos (token do webhook e segredo do ERP) são criptografados em repouso e NUNCA são devolvidos nem registrados em log.
// Não existe janela de horário: a busca da fila roda a cada N minutos sempre que o servidor estiver ligado.
@Service
public class InfinitePayAutomaticoService {

    private final InfinitePayConfiguracaoRepository repository;
    private final SegredoCriptografiaService criptografia;
    private final UsuarioResumoMapper usuarioResumoMapper;
    private final BancoRepository bancoRepository;
    private final FormaPagamentoRepository formaRepository;
    private final boolean permitirUrlLocal;

    public InfinitePayAutomaticoService(InfinitePayConfiguracaoRepository repository, SegredoCriptografiaService criptografia,
                                        UsuarioResumoMapper usuarioResumoMapper, BancoRepository bancoRepository, FormaPagamentoRepository formaRepository,
                                        @Value("${app.infinitepay.receptor-permitir-url-local:false}") boolean permitirUrlLocal) {
        this.repository = repository;
        this.criptografia = criptografia;
        this.usuarioResumoMapper = usuarioResumoMapper;
        this.bancoRepository = bancoRepository;
        this.formaRepository = formaRepository;
        this.permitirUrlLocal = permitirUrlLocal;
    }

    // endereço e segredo de saída do ERP para falar com o receptor
    public record Receptor(String url, String segredoErp) {
    }

    @Transactional(readOnly = true)
    public InfinitePayDTOs.AutomaticoResponse obter() {
        return resposta(repository.findById(InfinitePayConfiguracao.ID_UNICO).orElseGet(InfinitePayConfiguracao::new));
    }

    @Transactional
    public InfinitePayDTOs.AutomaticoResponse salvar(InfinitePayDTOs.AutomaticoRequest r) {
        if (r == null) throw new ItemInvalidoException("Informe os dados da configuração.");
        InfinitePayConfiguracao c = repository.findById(InfinitePayConfiguracao.ID_UNICO).orElseGet(InfinitePayConfiguracao::new);
        if (r.receptorUrl() != null) c.setReceptorUrl(ReceptorCalculo.validarUrl(r.receptorUrl(), permitirUrlLocal));
        if (r.tokenEntrada() != null && !r.tokenEntrada().isBlank()) {
            c.setReceptorTokenEntradaCriptografado(criptografar(ReceptorCalculo.validarSegredo("O token de entrada do receptor", r.tokenEntrada())));
        }
        if (r.segredoErp() != null && !r.segredoErp().isBlank()) {
            String segredo = ReceptorCalculo.validarSegredo("O segredo de acesso do ERP ao receptor", r.segredoErp());
            if (r.tokenEntrada() != null && segredo.equals(r.tokenEntrada().trim())) {
                throw new ItemInvalidoException("O segredo de acesso do ERP deve ser diferente do token de entrada do receptor.");
            }
            c.setReceptorSegredoErpCriptografado(criptografar(segredo));
        }
        if (r.intervaloFilaMinutos() != null) c.setIntervaloFilaMinutos(ReceptorCalculo.validarIntervalo(r.intervaloFilaMinutos()));
        if (r.modoAutomaticoPadrao() != null) c.setModoAutomaticoPadrao(r.modoAutomaticoPadrao());
        if (r.bancoPadraoId() != null) c.setBancoPadrao(r.bancoPadraoId() == 0 ? null : banco(r.bancoPadraoId()));
        if (r.formaPixId() != null) c.setFormaPix(r.formaPixId() == 0 ? null : forma(r.formaPixId(), "do PIX"));
        if (r.formaCartaoId() != null) c.setFormaCartao(r.formaCartaoId() == 0 ? null : forma(r.formaCartaoId(), "do cartão de crédito"));
        if (r.baixaAutomaticaAtiva() != null) {
            // o interruptor só liga com a configuração completa; desligar é sempre permitido (parada de emergência)
            if (r.baixaAutomaticaAtiva() && !pendencias(c).isEmpty()) {
                throw new ObjetoEmUsoException("Não é possível ligar a baixa automática: " + String.join("; ", pendencias(c)) + ".");
            }
            c.setBaixaAutomaticaAtiva(r.baixaAutomaticaAtiva());
        }
        if (c.isBaixaAutomaticaAtiva() && !pendencias(c).isEmpty()) {
            throw new ObjetoEmUsoException("A baixa automática está ligada e a configuração ficaria incompleta: " + String.join("; ", pendencias(c)) + ".");
        }
        return resposta(repository.save(c));
    }

    // endereço e segredo para buscar a fila; sem configuração completa: 409 formal
    @Transactional(readOnly = true)
    public Receptor receptor() {
        InfinitePayConfiguracao c = repository.findById(InfinitePayConfiguracao.ID_UNICO).orElseGet(InfinitePayConfiguracao::new);
        if (vazio(c.getReceptorUrl()) || vazio(c.getReceptorSegredoErpCriptografado())) {
            throw new ObjetoEmUsoException("Configure o receptor de pagamentos (endereço e segredo) em Sistema > InfinitePay.");
        }
        return new Receptor(c.getReceptorUrl(), descriptografar(c.getReceptorSegredoErpCriptografado()));
    }

    // webhook do pedido de criação do link automático. Recusa gerar quando a configuração está incompleta ou a baixa automática está desligada.
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> webhookParaLink() {
        InfinitePayConfiguracao c = repository.findById(InfinitePayConfiguracao.ID_UNICO).orElseGet(InfinitePayConfiguracao::new);
        exigirPronto(c);
        // a URL salva já foi validada; revalida (a propriedade de URL local pode ter mudado) para nunca gravar um webhook inutilizável no link
        String url = ReceptorCalculo.validarUrl(c.getReceptorUrl(), permitirUrlLocal);
        return ReceptorCalculo.webhook(url, descriptografar(c.getReceptorTokenEntradaCriptografado()));
    }

    // banco padrão da configuração (pode ser nulo)
    @Transactional(readOnly = true)
    public Banco bancoPadrao() {
        return repository.findById(InfinitePayConfiguracao.ID_UNICO).map(InfinitePayConfiguracao::getBancoPadrao).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean modoAutomaticoPadrao() {
        return repository.findById(InfinitePayConfiguracao.ID_UNICO).map(InfinitePayConfiguracao::isModoAutomaticoPadrao).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean baixaAutomaticaAtiva() {
        return repository.findById(InfinitePayConfiguracao.ID_UNICO).map(InfinitePayConfiguracao::isBaixaAutomaticaAtiva).orElse(false);
    }

    @Transactional(readOnly = true)
    public int intervaloFilaMinutos() {
        return repository.findById(InfinitePayConfiguracao.ID_UNICO).map(InfinitePayConfiguracao::getIntervaloFilaMinutos).orElse(2);
    }

    // forma de pagamento para o meio de captura da InfinitePay (pix / credit_card); desconhecido ou não mapeado: nulo (a fase 3 manda para revisão)
    @Transactional(readOnly = true)
    public FormaPagamento formaPorCaptura(String captureMethod) {
        InfinitePayConfiguracao c = repository.findById(InfinitePayConfiguracao.ID_UNICO).orElse(null);
        if (c == null || captureMethod == null) return null;
        String m = captureMethod.trim().toLowerCase(java.util.Locale.ROOT);
        if (m.equals("pix")) return c.getFormaPix();
        if (m.equals("credit_card") || m.equals("credit") || m.equals("card")) return c.getFormaCartao();
        return null;
    }

    private void exigirPronto(InfinitePayConfiguracao c) {
        List<String> p = pendencias(c);
        if (!p.isEmpty()) throw new ObjetoEmUsoException("Não é possível gerar link com baixa automática: " + String.join("; ", p) + ".");
        if (!c.isBaixaAutomaticaAtiva()) {
            throw new ObjetoEmUsoException("A baixa automática está desligada em Sistema > InfinitePay. Ligue-a para gerar links com baixa automática.");
        }
    }

    // o que falta para a baixa automática funcionar (InfiniteTag e chave também são necessárias para confirmar os pagamentos)
    static List<String> pendencias(InfinitePayConfiguracao c) {
        List<String> p = new ArrayList<>();
        if (!InfinitePayConfiguracaoService.configurada(c)) p.add("informe a InfiniteTag e a chave da InfinitePay");
        if (vazio(c.getReceptorUrl())) p.add("informe a URL do receptor de pagamentos");
        if (vazio(c.getReceptorTokenEntradaCriptografado())) p.add("informe o token de entrada do receptor");
        if (vazio(c.getReceptorSegredoErpCriptografado())) p.add("informe o segredo de acesso do ERP ao receptor");
        if (c.getFormaPix() == null) p.add("selecione a forma de pagamento do PIX");
        if (c.getFormaCartao() == null) p.add("selecione a forma de pagamento do cartão de crédito");
        return p;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private Banco banco(Long id) {
        Banco b = bancoRepository.findById(id).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        if (!b.isAtivo()) throw new ItemInvalidoException("O banco \"" + b.getNome() + "\" está desativado.");
        return b;
    }

    private FormaPagamento forma(Long id, String qual) {
        FormaPagamento f = formaRepository.findById(id).orElseThrow(() -> new ItemInvalidoException("A forma de pagamento " + qual + " não foi encontrada."));
        if (!f.isAtivo()) throw new ItemInvalidoException("A forma de pagamento \"" + f.getNome() + "\" está desativada.");
        return f;
    }

    private String criptografar(String valor) {
        try {
            return criptografia.criptografar(valor);
        } catch (IllegalStateException e) {
            throw new ObjetoEmUsoException("Não foi possível gravar o segredo com segurança.");
        }
    }

    private String descriptografar(String valor) {
        try {
            return criptografia.descriptografar(valor);
        } catch (SegredoCriptografiaService.SegredoIlegivelException e) {
            throw new ObjetoEmUsoException("Um segredo do receptor não pôde ser lido. Informe-o novamente em Sistema > InfinitePay.");
        }
    }

    private String ultimos(String criptografado) {
        if (vazio(criptografado)) return null;
        try {
            return LinkPagamentoCalculo.ultimosCaracteres(criptografia.descriptografar(criptografado));
        } catch (SegredoCriptografiaService.SegredoIlegivelException e) {
            return "****";
        }
    }

    private InfinitePayDTOs.Ref ref(Banco b) {
        return b == null ? null : new InfinitePayDTOs.Ref(b.getId(), b.getNome());
    }

    private InfinitePayDTOs.Ref ref(FormaPagamento f) {
        return f == null ? null : new InfinitePayDTOs.Ref(f.getId(), f.getNome());
    }

    private InfinitePayDTOs.AutomaticoResponse resposta(InfinitePayConfiguracao c) {
        List<String> p = pendencias(c);
        return new InfinitePayDTOs.AutomaticoResponse(c.isBaixaAutomaticaAtiva(), c.isModoAutomaticoPadrao(), c.getReceptorUrl(),
                !vazio(c.getReceptorTokenEntradaCriptografado()), ultimos(c.getReceptorTokenEntradaCriptografado()),
                !vazio(c.getReceptorSegredoErpCriptografado()), ultimos(c.getReceptorSegredoErpCriptografado()), c.getIntervaloFilaMinutos(),
                ref(c.getBancoPadrao()), ref(c.getFormaPix()), ref(c.getFormaCartao()), p.isEmpty(), p, c.getAtualizadoEm(),
                usuarioResumoMapper.toResumoDTO(c.getAtualizadoPor()));
    }
}
