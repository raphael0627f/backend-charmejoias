package com.manager.app.infinitepay;

import com.manager.app.email.ConfiguracaoEmailService;
import com.manager.app.email.EmailErros;
import com.manager.app.email.EmailFalhaEnvioException;
import com.manager.app.email.EmailTemplateService;
import com.manager.app.financeiro.ContaReceber;
import com.manager.app.financeiro.ContaReceberRecebimento;
import com.manager.app.financeiro.ContaReceberRecebimentoRepository;
import com.manager.app.financeiro.ContaReceberService;
import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import com.manager.app.usuario.UsuarioResumoMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

// links de pagamento da InfinitePay (modalidade manual): geração com bloqueio do título, listagem, marcação de pago e envio por e-mail (atrás de flag)
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkPagamentoService {

    static final int JANELA_DUPLO_CLIQUE_MINUTOS = 2;
    static final int MAX_DESTINATARIOS = 5;

    private final ContaReceberService contaReceberService;
    private final ContaReceberLinkPagamentoRepository repository;
    private final ContaReceberRecebimentoRepository recebimentoRepository;
    private final InfinitePayConfiguracaoService configuracaoService;
    private final InfinitePayClient client;
    private final LinkPagamentoCicloVida cicloVida;
    private final UsuarioAuditorAware auditor;
    private final UsuarioResumoMapper usuarioResumoMapper;
    private final ConfiguracaoEmailService configuracaoEmail;
    private final EmailTemplateService templateService;

    public record Resultado(InfinitePayDTOs.LinkResponse link, boolean reaproveitado) {
    }

    // ---------- geração ----------

    // o título fica bloqueado durante toda a geração: duas requisições simultâneas se serializam e a segunda enxerga a primeira
    @Transactional
    public Resultado gerar(Long tituloId, InfinitePayDTOs.LinkRequest r) {
        ContaReceber titulo = contaReceberService.buscarComLock(tituloId);
        InfinitePayConfiguracaoService.Credenciais credenciais = configuracaoService.credenciais();
        if (titulo.getSituacao() != SituacaoContaReceber.ABERTO && titulo.getSituacao() != SituacaoContaReceber.RECEBIDO_PARCIAL) {
            throw new ItemInvalidoException("Somente títulos em aberto ou parcialmente recebidos geram link de pagamento.");
        }
        InfinitePayConfiguracao padrao = configuracaoService.atual();
        InfinitePayDTOs.LinkRequest req = r == null ? new InfinitePayDTOs.LinkRequest(null, null, null, null, null, null, null, null, null, null) : r;

        BigDecimal saldo = titulo.getValor().subtract(titulo.getValorRecebido() == null ? BigDecimal.ZERO : titulo.getValorRecebido());
        boolean pix = req.pixHabilitado() != null ? req.pixHabilitado() : padrao.isPixHabilitado();
        BigDecimal descontoPix = LinkPagamentoCalculo.validarDesconto("do PIX", req.descontoPix() != null ? req.descontoPix() : padrao.getDescontoPix());
        BigDecimal descontoCartao = LinkPagamentoCalculo.validarDesconto("do cartão", req.descontoCartao() != null ? req.descontoCartao() : padrao.getDescontoCartao());
        if (!pix && descontoPix.signum() > 0) throw new ItemInvalidoException("O desconto do PIX não pode ser informado com o PIX desabilitado.");
        LinkPagamentoCalculo.Dados dados = new LinkPagamentoCalculo.Dados(
                LinkPagamentoCalculo.validarValor(req.valor(), saldo),
                LinkPagamentoCalculo.validarDescricao(req.descricao(), titulo.getDescricao()),
                LinkPagamentoCalculo.validarValidade(req.validade(), LocalDate.now(LinkPagamentoCalculo.FUSO)),
                req.pagamentoUnico() != null ? req.pagamentoUnico() : padrao.isPagamentoUnico(),
                LinkPagamentoCalculo.validarTaxa(req.taxaParcelamentoAssumida() != null ? req.taxaParcelamentoAssumida() : padrao.getAssuredInstallments()),
                pix, descontoPix, descontoCartao,
                req.exigeEnderecoEntrega() != null ? req.exigeEnderecoEntrega() : padrao.isExigeEndereco(),
                clienteEnviado(titulo, req.cliente()));

        // duplo clique: mesmo título, mesmos dados, janela curta => devolve o link existente em vez de criar outro
        LocalDateTime limite = LocalDateTime.now().minusMinutes(JANELA_DUPLO_CLIQUE_MINUTOS);
        for (ContaReceberLinkPagamento existente : repository.findByContaReceberIdAndStatusAndCriadoEmAfter(titulo.getId(), StatusLinkPagamento.ATIVO, limite)) {
            if (LinkPagamentoCalculo.mesmosDados(dados, existente)) return new Resultado(resposta(existente, padrao, true), true);
        }

        String orderNsu = "CR-" + titulo.getNumero() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        InfinitePayClient.LinkCriado criado = client.criarLink(credenciais.chave(), LinkPagamentoCalculo.montarPedido(credenciais.handle(), orderNsu, dados));

        ContaReceberLinkPagamento l = new ContaReceberLinkPagamento();
        l.setContaReceber(titulo);
        l.setOrderNsu(orderNsu);
        l.setUrl(criado.url());
        l.setValor(dados.valor());
        l.setDescricao(dados.descricao());
        l.setValidade(dados.validade());
        l.setPagamentoUnico(dados.pagamentoUnico());
        l.setAssuredInstallments(dados.taxaParcelamentoAssumida());
        l.setPixHabilitado(dados.pixHabilitado());
        l.setDescontoPix(dados.descontoPix());
        l.setDescontoCartao(dados.descontoCartao());
        l.setExigeEndereco(dados.exigeEndereco());
        l.setClienteNome(dados.cliente().nome());
        l.setClienteEmail(dados.cliente().email());
        l.setClienteTelefone(dados.cliente().telefone());
        l.setStatus(StatusLinkPagamento.ATIVO);
        l.setRespostaBruta(criado.respostaBruta());
        l.setCriadoPor(auditor.getCurrentAuditor().orElse(null));
        l.setCriadoEm(LocalDateTime.now());
        l = repository.save(l);
        cicloVida.registrar(titulo, "LINK_PAGAMENTO_GERADO", "Link de pagamento nº " + l.getId() + " gerado no valor de " + LinkPagamentoCalculo.moeda(l.getValor())
                + " (" + (l.getValidade() == null ? "sem validade" : "válido até " + LinkPagamentoCalculo.data(l.getValidade()))
                + "; PIX " + (l.isPixHabilitado() ? "habilitado" : "desabilitado") + "; pagamento " + (l.isPagamentoUnico() ? "único" : "não único") + ").");
        return new Resultado(resposta(l, padrao, false), false);
    }

    // sem objeto cliente no pedido: usa o cadastro do cliente do título (ou o nome do pagador avulso); só o que for válido é enviado
    private LinkPagamentoCalculo.Cliente clienteEnviado(ContaReceber titulo, InfinitePayDTOs.ClienteRequest c) {
        if (c != null) return LinkPagamentoCalculo.normalizarCliente(c.nome(), c.email(), c.telefone());
        if (titulo.getCliente() != null) {
            return new LinkPagamentoCalculo.Cliente(LinkPagamentoCalculo.normalizarCliente(titulo.getCliente().getNome(), null, null).nome(),
                    LinkPagamentoCalculo.emailValido(titulo.getCliente().getEmail()) ? titulo.getCliente().getEmail().trim() : null,
                    LinkPagamentoCalculo.telefoneDoCadastro(titulo.getCliente().getCelular(), titulo.getCliente().getTelefone()));
        }
        return LinkPagamentoCalculo.normalizarCliente(titulo.getPagadorNome(), null, null);
    }

    // ---------- consulta ----------

    @Transactional(readOnly = true)
    public List<InfinitePayDTOs.LinkResponse> listar(Long tituloId) {
        contaReceberService.buscar(tituloId);
        InfinitePayConfiguracao padrao = configuracaoService.atual();
        return repository.findByContaReceberIdOrderByIdDesc(tituloId).stream().map(l -> resposta(l, padrao, false)).toList();
    }

    // ---------- marcar como pago ----------

    @Transactional
    public InfinitePayDTOs.LinkResponse marcarPago(Long linkId, InfinitePayDTOs.MarcarPagoRequest r) {
        Long tituloId = repository.findContaReceberIdById(linkId).orElseThrow(() -> new ObjetoNaoEncontradoException("Link de pagamento não encontrado."));
        ContaReceber titulo = contaReceberService.buscarComLock(tituloId);
        ContaReceberLinkPagamento link = repository.findById(linkId).orElseThrow(() -> new ObjetoNaoEncontradoException("Link de pagamento não encontrado."));
        if (link.getStatus() != StatusLinkPagamento.ATIVO) {
            throw new ItemInvalidoException("Somente links ativos podem ser marcados como pagos.");
        }
        Long recebimentoId = r == null ? null : r.recebimentoId();
        if (recebimentoId != null) {
            ContaReceberRecebimento rec = recebimentoRepository.findById(recebimentoId)
                    .orElseThrow(() -> new ItemInvalidoException("O recebimento informado não foi encontrado."));
            if (!rec.getContaReceber().getId().equals(titulo.getId())) throw new ItemInvalidoException("O recebimento informado não pertence ao título do link.");
            if (rec.isEstornada()) throw new ItemInvalidoException("O recebimento informado foi estornado.");
        }
        cicloVida.marcarPago(link, recebimentoId, r == null ? null : r.observacao());
        return resposta(link, configuracaoService.atual(), false);
    }

    // ---------- e-mail (atrás de flag) ----------

    // o envio usa a configuração de e-mail do sistema (Gmail real): fica desabilitado por padrão; nenhum teste automatizado o aciona
    @Transactional
    public InfinitePayDTOs.EnviarEmailResponse enviarEmail(Long linkId, InfinitePayDTOs.EnviarEmailRequest r) {
        if (!configuracaoService.envioEmailHabilitado()) throw new ObjetoEmUsoException("O envio por e-mail está desabilitado no momento.");
        Long tituloId = repository.findContaReceberIdById(linkId).orElseThrow(() -> new ObjetoNaoEncontradoException("Link de pagamento não encontrado."));
        ContaReceber titulo = contaReceberService.buscarComLock(tituloId);
        ContaReceberLinkPagamento link = repository.findById(linkId).orElseThrow(() -> new ObjetoNaoEncontradoException("Link de pagamento não encontrado."));
        if (link.getStatus() != StatusLinkPagamento.ATIVO) throw new ObjetoEmUsoException("Somente links ativos podem ser enviados.");
        List<String> para = new ArrayList<>();
        if (r != null && r.para() != null) for (String p : new LinkedHashSet<>(r.para())) para.add(p == null ? "" : p.trim());
        if (para.isEmpty()) throw new ItemInvalidoException("Informe ao menos um destinatário.");
        if (para.size() > MAX_DESTINATARIOS) throw new ItemInvalidoException("Informe no máximo " + MAX_DESTINATARIOS + " destinatários.");
        for (String p : para) if (!LinkPagamentoCalculo.emailValido(p)) throw new ItemInvalidoException("O endereço de e-mail \"" + p + "\" é inválido.");
        String assunto = r.assunto() == null || r.assunto().isBlank() ? "Link para pagamento" : r.assunto().trim();
        if (assunto.contains("\r") || assunto.contains("\n")) throw new ItemInvalidoException("O assunto não pode conter quebras de linha.");
        InfinitePayConfiguracao padrao = configuracaoService.atual();
        String mensagem = r.mensagem() == null || r.mensagem().isBlank()
                ? LinkPagamentoCalculo.renderizar(padrao.getTextoEmail(), LinkPagamentoCalculo.TEXTO_EMAIL_PADRAO, link.getClienteNome(), link.getValor(), link.getDescricao(), link.getUrl(), link.getValidade())
                : r.mensagem().trim();

        ConfiguracaoEmailService.EnvioEmail envio = configuracaoEmail.resolverParaEnvio();
        JavaMailSender sender = envio.sender();
        List<EmailTemplateService.Linha> linhas = List.of(
                new EmailTemplateService.Linha("Valor", LinkPagamentoCalculo.moeda(link.getValor())),
                new EmailTemplateService.Linha("Descrição", link.getDescricao()),
                new EmailTemplateService.Linha("Validade", link.getValidade() == null ? "Sem data de expiração" : LinkPagamentoCalculo.data(link.getValidade())),
                new EmailTemplateService.Linha("Link", link.getUrl()));
        EmailTemplateService.Conteudo conteudo = templateService.montar(new EmailTemplateService.Modelo(assunto, mensagem, "Dados do pagamento", linhas, templateService.empresaPadrao()));
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        try {
            MimeMessage mensagemMime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagemMime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            ConfiguracaoEmailService.definirRemetente(helper, envio.config());
            helper.setTo(para.toArray(new String[0]));
            helper.setSubject(assunto);
            templateService.aplicar(helper, conteudo);
            sender.send(mensagemMime);
        } catch (MessagingException | MailException | java.io.UnsupportedEncodingException e) {
            log.error("Falha ao enviar e-mail do link de pagamento nº {}: {}: {}", link.getId(), e.getClass().getSimpleName(), e.getMessage());
            throw new EmailFalhaEnvioException("Não foi possível enviar o e-mail: " + EmailErros.descrever(e) + " Verifique a configuração do e-mail em Sistema ou tente novamente mais tarde.", e);
        }
        cicloVida.registrar(titulo, "LINK_PAGAMENTO_ENVIADO", "Link de pagamento nº " + link.getId() + " enviado por e-mail a " + String.join(", ", para) + ".");
        return new InfinitePayDTOs.EnviarEmailResponse(para);
    }

    // ---------- resposta ----------

    private InfinitePayDTOs.LinkResponse resposta(ContaReceberLinkPagamento l, InfinitePayConfiguracao padrao, boolean reaproveitado) {
        boolean expirado = l.getStatus() == StatusLinkPagamento.ATIVO && l.getValidade() != null && l.getValidade().isBefore(LocalDate.now(LinkPagamentoCalculo.FUSO));
        String nome = l.getClienteNome();
        return new InfinitePayDTOs.LinkResponse(l.getId(), l.getContaReceber().getId(), l.getContaReceber().getNumero(), l.getOrderNsu(), l.getUrl(), l.getValor(),
                l.getDescricao(), l.getValidade(), expirado, l.isPagamentoUnico(), l.getAssuredInstallments(), l.isPixHabilitado(), l.getDescontoPix(),
                l.getDescontoCartao(), l.isExigeEndereco(), new InfinitePayDTOs.ClienteResponse(l.getClienteNome(), l.getClienteEmail(), l.getClienteTelefone()),
                l.getStatus().name(), l.getRecebimentoId(), l.getCriadoEm(), usuarioResumoMapper.toResumoDTO(l.getCriadoPor()), reaproveitado,
                LinkPagamentoCalculo.renderizar(padrao.getTextoWhatsapp(), LinkPagamentoCalculo.TEXTO_WHATSAPP_PADRAO, nome, l.getValor(), l.getDescricao(), l.getUrl(), l.getValidade()),
                LinkPagamentoCalculo.renderizar(padrao.getTextoEmail(), LinkPagamentoCalculo.TEXTO_EMAIL_PADRAO, nome, l.getValor(), l.getDescricao(), l.getUrl(), l.getValidade()));
    }
}
