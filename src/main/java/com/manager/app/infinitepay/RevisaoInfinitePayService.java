package com.manager.app.infinitepay;

import com.manager.app.financeiro.Banco;
import com.manager.app.financeiro.BancoRepository;
import com.manager.app.financeiro.ContaReceber;
import com.manager.app.financeiro.ContaReceberCalculo;
import com.manager.app.financeiro.ContaReceberMapper;
import com.manager.app.financeiro.ContaReceberRecebimento;
import com.manager.app.financeiro.ContaReceberRecebimentoService;
import com.manager.app.financeiro.ContaReceberService;
import com.manager.app.financeiro.FormaPagamento;
import com.manager.app.financeiro.FormaPagamentoRepository;
import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.infinitepay.dto.RevisaoDTOs;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// fila de revisão da baixa automática: lista, detalhe, aplicar no título, ignorar com motivo e reprocessar. Tudo que altera estado roda na mesma thread
// serializada do ciclo da fila (BaixaAutomaticaService.serializado), então nunca disputa o mesmo evento com a busca automática.
// Estados de exibição RESOLVIDO_APLICADO / RESOLVIDO_IGNORADO são derivados (evento PROCESSADO/IGNORADO com resolvido_em preenchido): sem migration.
@Service
public class RevisaoInfinitePayService {

    static final String APLICAR = "APLICAR";
    static final String IGNORAR = "IGNORAR";
    static final String REPROCESSAR = "REPROCESSAR";

    private final InfinitePayEventoRepository eventos;
    private final ContaReceberLinkPagamentoRepository links;
    private final ContaReceberService contaReceberService;
    private final ContaReceberRecebimentoService recebimentoService;
    private final FormaPagamentoRepository formaRepository;
    private final BancoRepository bancoRepository;
    private final InfinitePayClient client;
    private final InfinitePayConfiguracaoService configuracao;
    private final InfinitePayAutomaticoService automatico;
    private final TravaAdvisory trava;
    private final BaixaAutomaticaService baixa;
    private final UsuarioAuditorAware auditor;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final TransactionTemplate txLeitura;

    public RevisaoInfinitePayService(InfinitePayEventoRepository eventos, ContaReceberLinkPagamentoRepository links, ContaReceberService contaReceberService,
                                     ContaReceberRecebimentoService recebimentoService, FormaPagamentoRepository formaRepository, BancoRepository bancoRepository,
                                     InfinitePayClient client, InfinitePayConfiguracaoService configuracao, InfinitePayAutomaticoService automatico, TravaAdvisory trava,
                                     BaixaAutomaticaService baixa, UsuarioAuditorAware auditor, ObjectMapper objectMapper, PlatformTransactionManager tm) {
        this.eventos = eventos;
        this.links = links;
        this.contaReceberService = contaReceberService;
        this.recebimentoService = recebimentoService;
        this.formaRepository = formaRepository;
        this.bancoRepository = bancoRepository;
        this.client = client;
        this.configuracao = configuracao;
        this.automatico = automatico;
        this.trava = trava;
        this.baixa = baixa;
        this.auditor = auditor;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(tm);
        this.txLeitura = new TransactionTemplate(tm);
        this.txLeitura.setReadOnly(true);
    }

    // ---------- consulta ----------

    static final java.util.Set<String> CAMPOS_ORDENAVEIS = java.util.Set.of("id", "criadoEm", "status", "motivoCodigo", "transactionNsu", "orderNsu", "tentativas", "resolvidoEm");

    // filtros e ordenação inválidos viram 400 formal (nunca erro técnico)
    static void validarListagem(String motivo, LocalDate de, LocalDate ate, Pageable pageable) {
        if (de != null && ate != null && de.isAfter(ate)) throw new ItemInvalidoException("A data inicial não pode ser posterior à data final.");
        if (motivo != null && !motivo.isBlank() && MotivoRevisao.de(motivo.trim().toUpperCase(Locale.ROOT)) == null) throw new ItemInvalidoException("Motivo de revisão inválido.");
        if (pageable != null) {
            for (org.springframework.data.domain.Sort.Order o : pageable.getSort()) {
                if (!CAMPOS_ORDENAVEIS.contains(o.getProperty())) throw new ItemInvalidoException("Campo de ordenação inválido.");
            }
        }
    }

    public Page<RevisaoDTOs.Resumo> listar(String status, String motivo, LocalDate de, LocalDate ate, Long tituloId, java.util.UUID clienteId, Pageable pageable) {
        validarListagem(motivo, de, ate, pageable);
        String st = status == null || status.isBlank() ? "REVISAO" : status.trim().toUpperCase(Locale.ROOT);
        Specification<InfinitePayEvento> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            switch (st) {
                case "RESOLVIDO_APLICADO" -> {
                    p.add(cb.isNotNull(root.get("resolvidoEm")));
                    p.add(cb.isNotNull(root.get("recebimentoId")));
                }
                case "RESOLVIDO_IGNORADO" -> {
                    p.add(cb.isNotNull(root.get("resolvidoEm")));
                    p.add(cb.isNull(root.get("recebimentoId")));
                }
                default -> {
                    InfinitePayEvento.Status s;
                    try {
                        s = InfinitePayEvento.Status.valueOf(st);
                    } catch (IllegalArgumentException e) {
                        throw new ItemInvalidoException("Situação de revisão inválida.");
                    }
                    p.add(cb.equal(root.get("status"), s));
                    p.add(cb.isNull(root.get("resolvidoEm")));
                }
            }
            if (motivo != null && !motivo.isBlank()) p.add(cb.equal(root.get("motivoCodigo"), motivo.trim().toUpperCase(Locale.ROOT)));
            if (de != null) p.add(cb.greaterThanOrEqualTo(root.get("criadoEm"), de.atStartOfDay()));
            if (ate != null) p.add(cb.lessThan(root.get("criadoEm"), ate.plusDays(1).atStartOfDay()));
            if (tituloId != null) p.add(cb.equal(root.get("link").get("contaReceber").get("id"), tituloId));
            if (clienteId != null) p.add(cb.equal(root.get("link").get("contaReceber").get("cliente").get("id"), clienteId));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return txLeitura.execute(s -> eventos.findAll(spec, pageable).map(this::resumo));
    }

    public RevisaoDTOs.Detalhe detalhe(Long id) {
        return txLeitura.execute(s -> detalheDe(buscar(id)));
    }

    private InfinitePayEvento buscar(Long id) {
        return eventos.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Revisão não encontrada."));
    }

    static String statusExibicao(InfinitePayEvento e) {
        if (e.getResolvidoEm() != null) return e.getRecebimentoId() != null ? "RESOLVIDO_APLICADO" : "RESOLVIDO_IGNORADO";
        return e.getStatus().name();
    }

    static String meio(String captura) {
        if (captura == null) return null;
        return switch (captura.toLowerCase(Locale.ROOT)) {
            case "pix" -> "PIX";
            case "credit_card" -> "Cartão de crédito";
            default -> captura;
        };
    }

    private RevisaoDTOs.Resumo resumo(InfinitePayEvento e) {
        ContaReceberLinkPagamento link = e.getLink();
        ContaReceber t = link == null ? null : link.getContaReceber();
        MotivoRevisao codigo = MotivoRevisao.de(e.getMotivoCodigo());
        Long diferenca = e.getAmountCentavos() == null || e.getPaidAmountCentavos() == null ? null : e.getPaidAmountCentavos() - e.getAmountCentavos();
        return new RevisaoDTOs.Resumo(e.getId(), e.getCriadoEm(), statusExibicao(e), e.getMotivoCodigo(), codigo == null ? null : codigo.rotulo(), e.getMotivo(), e.getEvento(),
                e.getOrderNsu(), e.getTransactionNsu(), t == null ? null : t.getId(), t == null ? null : t.getNumero(),
                t == null ? null : ContaReceberMapper.pagadorExibicao(t), link == null ? null : link.getValor(), e.getAmountCentavos(), e.getPaidAmountCentavos(), diferenca,
                e.getCaptureMethod(), meio(e.getCaptureMethod()), e.getTentativas(), e.getRecebimentoId(), e.getResolvidoEm(),
                e.getResolvidoPor() == null ? null : nome(e.getResolvidoPor()), e.getResolucao());
    }

    private static String nome(Usuario u) {
        String n = ((u.getPrimeiroNome() == null ? "" : u.getPrimeiroNome()) + " " + (u.getSobrenome() == null ? "" : u.getSobrenome())).trim();
        return n.isEmpty() ? u.getUsuario() : n;
    }

    private RevisaoDTOs.Detalhe detalheDe(InfinitePayEvento e) {
        MotivoRevisao codigo = MotivoRevisao.de(e.getMotivoCodigo());
        boolean aberto = e.getResolvidoEm() == null && (e.getStatus() == InfinitePayEvento.Status.REVISAO || e.getStatus() == InfinitePayEvento.Status.ERRO_TEMPORARIO);
        List<String> acoes = new ArrayList<>();
        if (aberto) {
            if (codigo != null && codigo.aplicavel() && e.getLink() != null) acoes.add(APLICAR);
            if (codigo == null || codigo.reprocessavel()) acoes.add(REPROCESSAR);
            acoes.add(IGNORAR);
        }
        ContaReceberLinkPagamento link = e.getLink();
        RevisaoDTOs.TituloResumo titulo = null;
        RevisaoDTOs.LinkResumo linkResumo = null;
        if (link != null) {
            ContaReceber t = link.getContaReceber();
            BigDecimal aberto2 = ContaReceberCalculo.valorAberto(t.getSituacao(), t.getValor(), t.getValorRecebido());
            titulo = new RevisaoDTOs.TituloResumo(t.getId(), t.getNumero(), t.getSituacao().name(), t.getValor(), t.getValorRecebido(), aberto2,
                    t.getCliente() == null ? null : t.getCliente().getId(), ContaReceberMapper.pagadorExibicao(t));
            linkResumo = new RevisaoDTOs.LinkResumo(link.getId(), link.getModo() == null ? null : link.getModo().name(), link.getStatus().name(), link.getValor(),
                    link.getBanco() == null ? null : link.getBanco().getId(), link.getBanco() == null ? null : link.getBanco().getNome(), link.getTransactionNsu());
        }
        return RevisaoDTOs.Detalhe.de(resumo(e), e.getPayload(), acoes, aberto && codigo != null && codigo.exigeBancoForma(), titulo, linkResumo);
    }

    // ---------- ações ----------

    private void exigirAberta(InfinitePayEvento e) {
        if (e.getResolvidoEm() != null || !(e.getStatus() == InfinitePayEvento.Status.REVISAO || e.getStatus() == InfinitePayEvento.Status.ERRO_TEMPORARIO)) {
            throw new ObjetoEmUsoException("Esta revisão já foi resolvida.");
        }
    }

    public RevisaoDTOs.Detalhe ignorar(Long id, RevisaoDTOs.IgnorarRequest req) {
        String motivo = req == null || req.motivo() == null ? "" : req.motivo().trim();
        if (motivo.length() < 5 || motivo.length() > 500) throw new ItemInvalidoException("Informe o motivo de ignorar o pagamento (de 5 a 500 caracteres).");
        Usuario quem = auditor.getCurrentAuditor().orElse(null);
        return baixa.serializado(() -> tx.execute(s -> {
            InfinitePayEvento e = buscar(id);
            exigirAberta(e);
            e.setStatus(InfinitePayEvento.Status.IGNORADO);
            e.setResolvidoEm(LocalDateTime.now());
            e.setResolvidoPor(quem);
            e.setResolucao(motivo);
            eventos.save(e);
            return detalheDe(e);
        }));
    }

    static final String AVISO_NAO_CONFIRMADO = "A InfinitePay não confirmou o pagamento neste momento. O item permanece em revisão.";
    static final String AVISO_CONSULTA_FALHOU = "Não foi possível consultar a InfinitePay agora. O item permanece em revisão; tente novamente mais tarde.";

    // o reprocessamento nunca move o item sozinho para "ignorado": se a InfinitePay não confirmar (ou a consulta falhar) ele volta exatamente como estava
    public RevisaoDTOs.Detalhe reprocessar(Long id, RevisaoDTOs.ReprocessarRequest req) {
        if (req == null || !Boolean.TRUE.equals(req.confirmar())) throw new ItemInvalidoException("Confirme o reprocessamento para continuar.");
        String[] aviso = new String[1];
        baixa.serializado(() -> {
            record Original(InfinitePayEvento.Status status, String codigo, String motivo, int tentativas, LocalDateTime processadoEm) {
            }
            Original original = tx.execute(s -> {
                InfinitePayEvento e = buscar(id);
                exigirAberta(e);
                MotivoRevisao codigo = MotivoRevisao.de(e.getMotivoCodigo());
                if (codigo != null && !codigo.reprocessavel()) throw new ItemInvalidoException("Este item não pode ser reprocessado.");
                Original o = new Original(e.getStatus(), e.getMotivoCodigo(), e.getMotivo(), e.getTentativas(), e.getProcessadoEm());
                e.setStatus(InfinitePayEvento.Status.RECEBIDO);
                e.setTentativas(0);
                eventos.save(e);
                return o;
            });
            baixa.processarEvento(id, new BaixaAutomaticaService.Resumo());
            tx.executeWithoutResult(s -> {
                InfinitePayEvento e = buscar(id);
                boolean naoConfirmou = e.getStatus() == InfinitePayEvento.Status.IGNORADO && e.getResolvidoEm() == null
                        && BaixaAutomaticaCalculo.MOTIVO_NAO_CONFIRMADO.equals(e.getMotivo());
                boolean consultaFalhou = e.getStatus() == InfinitePayEvento.Status.ERRO_TEMPORARIO;
                if (naoConfirmou || consultaFalhou) {
                    e.setStatus(original.status());
                    e.setMotivoCodigo(original.codigo());
                    e.setMotivo(original.motivo());
                    e.setTentativas(original.tentativas());
                    e.setProcessadoEm(original.processadoEm());
                    eventos.save(e);
                    aviso[0] = naoConfirmou ? AVISO_NAO_CONFIRMADO : AVISO_CONSULTA_FALHOU;
                }
            });
            return null;
        });
        RevisaoDTOs.Detalhe d = detalhe(id);
        return aviso[0] == null ? d : d.comAviso(aviso[0]);
    }

    public RevisaoDTOs.Detalhe aplicar(Long id, RevisaoDTOs.AplicarRequest req) {
        RevisaoDTOs.AplicarRequest r = req == null ? new RevisaoDTOs.AplicarRequest(null, null, null) : req;
        Usuario quem = auditor.getCurrentAuditor().orElse(null); // capturado na thread da requisição; a baixa em si roda sem usuário
        baixa.serializado(() -> {
            aplicarSerializado(id, r, quem);
            return null;
        });
        return detalhe(id);
    }

    private void aplicarSerializado(Long id, RevisaoDTOs.AplicarRequest req, Usuario quem) {
        record Preparo(Long linkId, Long tituloId, BaixaAutomaticaCalculo.Payload payload) {
        }
        Preparo preparo = tx.execute(s -> {
            InfinitePayEvento e = buscar(id);
            exigirAberta(e);
            MotivoRevisao codigo = MotivoRevisao.de(e.getMotivoCodigo());
            if (codigo == null || !codigo.aplicavel() || e.getLink() == null) {
                throw new ItemInvalidoException("Este pagamento não pode ser aplicado ao título (" + (codigo == null ? "motivo não identificado" : codigo.rotulo().toLowerCase(Locale.ROOT))
                        + "). Ignore-o informando o motivo, se for o caso.");
            }
            BaixaAutomaticaCalculo.Payload p = BaixaAutomaticaCalculo.ler(e.getPayload(), objectMapper);
            if (p.orderNsu() == null || p.transactionNsu() == null || p.slug() == null) throw new ItemInvalidoException("O registro do pagamento está incompleto e não pode ser aplicado.");
            return new Preparo(e.getLink().getId(), e.getLink().getContaReceber().getId(), p);
        });
        BaixaAutomaticaCalculo.Payload p = preparo.payload();
        // a palavra final continua sendo a InfinitePay: o pagamento é confirmado de novo antes de qualquer baixa
        InfinitePayConfiguracaoService.Credenciais c = configuracao.credenciais();
        InfinitePayClient.PagamentoVerificado check = client.verificarPagamento(c.chave(), c.handle(), p.orderNsu(), p.transactionNsu(), p.slug());
        if (!check.success() || !check.paid()) throw new ItemInvalidoException("A InfinitePay não confirma este pagamento no momento. Tente novamente mais tarde.");

        tx.executeWithoutResult(s -> {
            ContaReceber titulo = contaReceberService.buscarComLock(preparo.tituloId());
            trava.travar(p.transactionNsu() + "|" + (p.evento() == null ? "" : p.evento()));
            InfinitePayEvento e = buscar(id);
            exigirAberta(e);
            ContaReceberLinkPagamento link = links.findById(preparo.linkId()).orElseThrow();
            if (titulo.getSituacao() == SituacaoContaReceber.CANCELADO) throw new ItemInvalidoException("O título está cancelado: não é possível aplicar o pagamento.");
            if (titulo.getSituacao() == SituacaoContaReceber.RECEBIDO) throw new ItemInvalidoException("O título já está quitado: não é possível aplicar o pagamento.");
            if (link.getStatus() != StatusLinkPagamento.ATIVO) throw new ItemInvalidoException("O link de pagamento já não está ativo: não é possível aplicar o pagamento.");
            if (check.amount() != BaixaAutomaticaCalculo.centavos(link.getValor())) {
                throw new ItemInvalidoException("O valor da cobrança confirmado pela InfinitePay diverge do valor do link: não é possível aplicar o pagamento.");
            }
            BigDecimal principal;
            BigDecimal taxa = BigDecimal.ZERO;
            if (check.paidAmount() < check.amount()) {
                if (!Boolean.TRUE.equals(req.confirmarValorMenor())) {
                    throw new ItemInvalidoException("O valor pago é menor que o cobrado. Confirme a baixa do valor efetivamente pago para continuar.");
                }
                principal = BigDecimal.valueOf(check.paidAmount()).movePointLeft(2);
            } else {
                principal = link.getValor();
                taxa = BigDecimal.valueOf(check.paidAmount() - check.amount()).movePointLeft(2);
            }
            BigDecimal aberto = ContaReceberCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorRecebido());
            if (aberto.compareTo(principal) < 0) throw new ItemInvalidoException("O saldo em aberto do título é menor que o valor pago: não é possível aplicar o pagamento.");
            Banco banco = req.bancoId() != null ? bancoRepository.findById(req.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."))
                    : link.getBanco();
            if (banco == null || !banco.isAtivo()) throw new ItemInvalidoException("O banco de destino está desativado ou não foi definido. Informe outro banco.");
            String captura = check.captureMethod() != null ? check.captureMethod() : p.captureMethod();
            FormaPagamento forma = req.formaPagamentoId() != null ? formaRepository.findById(req.formaPagamentoId())
                    .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada.")) : automatico.formaPorCaptura(captura);
            if (forma == null) throw new ItemInvalidoException("A forma de pagamento do meio utilizado não está configurada. Informe banco e forma de pagamento.");
            LocalDate data = e.getCriadoEm().toLocalDate();
            String obs = BaixaAutomaticaCalculo.observacao(new BaixaAutomaticaCalculo.Payload(true, p.evento(), p.orderNsu(), p.transactionNsu(), p.slug(), check.amount(),
                    check.paidAmount(), captura, check.installments() != null ? check.installments() : p.installments(), List.of()), taxa);
            obs = (obs.length() > 470 ? obs.substring(0, 470) : obs) + " Aplicado após revisão.";
            ContaReceberRecebimento rec = recebimentoService.receberAutomatico(titulo, banco.getId(), forma, data, principal, taxa, obs, link, p.transactionNsu());
            e.setStatus(InfinitePayEvento.Status.PROCESSADO);
            e.setRecebimentoId(rec.getId());
            e.setResolvidoEm(LocalDateTime.now());
            e.setResolvidoPor(quem);
            e.setResolucao("Aplicado ao título (recebimento nº " + rec.getId() + ").");
            e.setProcessadoEm(LocalDateTime.now());
            eventos.save(e);
        });
    }
}
