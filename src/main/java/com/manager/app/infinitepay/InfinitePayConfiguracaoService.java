package com.manager.app.infinitepay;

import com.manager.app.email.SegredoCriptografiaService;
import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// configuração da InfinitePay (InfiniteTag, chave da API e padrões). A chave é criptografada em repouso (mesmo serviço da senha do SMTP)
// e NUNCA é devolvida nem registrada em log: a API informa só se existe e os 4 últimos caracteres mascarados.
@Service
public class InfinitePayConfiguracaoService {

    public static final String MSG_NAO_CONFIGURADA = "Configure a InfinitePay em Sistema antes de gerar links.";

    private final InfinitePayConfiguracaoRepository repository;
    private final SegredoCriptografiaService criptografia;
    private final UsuarioResumoMapper usuarioResumoMapper;
    private final boolean envioEmailHabilitado;

    public InfinitePayConfiguracaoService(InfinitePayConfiguracaoRepository repository, SegredoCriptografiaService criptografia,
                                          UsuarioResumoMapper usuarioResumoMapper,
                                          @Value("${app.infinitepay.envio-email-enabled:false}") boolean envioEmailHabilitado) {
        this.repository = repository;
        this.criptografia = criptografia;
        this.usuarioResumoMapper = usuarioResumoMapper;
        this.envioEmailHabilitado = envioEmailHabilitado;
    }

    public record Credenciais(String handle, String chave) {
    }

    public boolean envioEmailHabilitado() {
        return envioEmailHabilitado;
    }

    @Transactional(readOnly = true)
    public InfinitePayDTOs.ConfiguracaoResponse obter() {
        InfinitePayConfiguracao c = repository.findById(InfinitePayConfiguracao.ID_UNICO).orElseGet(InfinitePayConfiguracao::new);
        return resposta(c);
    }

    // padrões do diálogo de geração (sem InfiniteTag, chave nem textos)
    @Transactional(readOnly = true)
    public InfinitePayDTOs.PadroesResponse padroes() {
        InfinitePayConfiguracao c = repository.findById(InfinitePayConfiguracao.ID_UNICO).orElseGet(InfinitePayConfiguracao::new);
        return new InfinitePayDTOs.PadroesResponse(configurada(c), c.getValidadeDias(), c.isPagamentoUnico(), c.getAssuredInstallments(),
                c.isPixHabilitado(), c.getDescontoPix(), c.getDescontoCartao(), c.isExigeEndereco(), envioEmailHabilitado);
    }

    @Transactional
    public InfinitePayDTOs.ConfiguracaoResponse salvar(InfinitePayDTOs.ConfiguracaoRequest r) {
        if (r == null) throw new ItemInvalidoException("Informe os dados da configuração.");
        InfinitePayConfiguracao c = repository.findById(InfinitePayConfiguracao.ID_UNICO).orElseGet(InfinitePayConfiguracao::new);
        if (r.handle() != null) {
            String h = LinkPagamentoCalculo.limparHandle(r.handle());
            LinkPagamentoCalculo.validarHandle(h);
            c.setHandle(h);
        }
        if (Boolean.TRUE.equals(r.removerChave())) {
            c.setApiKeyCriptografada(null);
        } else if (r.chave() != null && !r.chave().isBlank()) {
            String chave = r.chave().trim();
            if (chave.length() > 200 || chave.contains(" ")) throw new ItemInvalidoException("A chave da API informada é inválida.");
            try {
                c.setApiKeyCriptografada(criptografia.criptografar(chave));
            } catch (IllegalStateException e) {
                throw new ObjetoEmUsoException("Não foi possível gravar a chave com segurança.");
            }
        } // chave vazia/ausente: mantém a anterior
        if (r.validadeDias() != null) {
            if (r.validadeDias() < 0 || r.validadeDias() > 365) throw new ItemInvalidoException("Os dias de validade padrão devem estar entre 0 e 365.");
            c.setValidadeDias(r.validadeDias());
        }
        if (r.pagamentoUnico() != null) c.setPagamentoUnico(r.pagamentoUnico());
        if (r.taxaParcelamentoAssumida() != null) c.setAssuredInstallments(LinkPagamentoCalculo.validarTaxa(r.taxaParcelamentoAssumida()));
        if (r.pixHabilitado() != null) c.setPixHabilitado(r.pixHabilitado());
        if (r.descontoPix() != null) c.setDescontoPix(LinkPagamentoCalculo.validarDesconto("do PIX", r.descontoPix()));
        if (r.descontoCartao() != null) c.setDescontoCartao(LinkPagamentoCalculo.validarDesconto("do cartão", r.descontoCartao()));
        if (r.exigeEnderecoEntrega() != null) c.setExigeEndereco(r.exigeEnderecoEntrega());
        if (r.textoWhatsapp() != null) c.setTextoWhatsapp(texto(r.textoWhatsapp(), "O texto do WhatsApp"));
        if (r.textoEmail() != null) c.setTextoEmail(texto(r.textoEmail(), "O texto do e-mail"));
        if (!c.isPixHabilitado() && c.getDescontoPix().signum() > 0) {
            throw new ItemInvalidoException("O desconto do PIX não pode ser informado com o PIX desabilitado.");
        }
        return resposta(repository.save(c));
    }

    // credenciais para chamar a InfinitePay; sem InfiniteTag ou sem chave: 409 formal
    @Transactional(readOnly = true)
    public Credenciais credenciais() {
        InfinitePayConfiguracao c = repository.findById(InfinitePayConfiguracao.ID_UNICO).orElse(null);
        if (c == null || !configurada(c)) throw new ObjetoEmUsoException(MSG_NAO_CONFIGURADA);
        try {
            return new Credenciais(c.getHandle(), criptografia.descriptografar(c.getApiKeyCriptografada()));
        } catch (SegredoCriptografiaService.SegredoIlegivelException e) {
            throw new ObjetoEmUsoException("A chave da InfinitePay não pôde ser lida. Informe-a novamente em Sistema.");
        }
    }

    @Transactional(readOnly = true)
    public InfinitePayConfiguracao atual() {
        return repository.findById(InfinitePayConfiguracao.ID_UNICO).orElseGet(InfinitePayConfiguracao::new);
    }

    static boolean configurada(InfinitePayConfiguracao c) {
        return c.getHandle() != null && !c.getHandle().isBlank() && c.getApiKeyCriptografada() != null && !c.getApiKeyCriptografada().isBlank();
    }

    private String texto(String t, String nome) {
        String v = t.trim();
        if (v.length() > 2000) throw new ItemInvalidoException(nome + " deve ter no máximo 2000 caracteres.");
        return v.isEmpty() ? null : v;
    }

    private InfinitePayDTOs.ConfiguracaoResponse resposta(InfinitePayConfiguracao c) {
        String final4 = null;
        boolean temChave = c.getApiKeyCriptografada() != null && !c.getApiKeyCriptografada().isBlank();
        if (temChave) {
            try {
                final4 = LinkPagamentoCalculo.ultimosCaracteres(criptografia.descriptografar(c.getApiKeyCriptografada()));
            } catch (SegredoCriptografiaService.SegredoIlegivelException e) {
                final4 = "****";
            }
        }
        BigDecimal pix = c.getDescontoPix() == null ? BigDecimal.ZERO : c.getDescontoPix();
        BigDecimal cartao = c.getDescontoCartao() == null ? BigDecimal.ZERO : c.getDescontoCartao();
        return new InfinitePayDTOs.ConfiguracaoResponse(c.getHandle(), temChave, final4, c.getValidadeDias(), c.isPagamentoUnico(), c.getAssuredInstallments(),
                c.isPixHabilitado(), pix, cartao, c.isExigeEndereco(), c.getTextoWhatsapp(), c.getTextoEmail(), envioEmailHabilitado,
                c.getAtualizadoEm(), usuarioResumoMapper.toResumoDTO(c.getAtualizadoPor()));
    }
}
