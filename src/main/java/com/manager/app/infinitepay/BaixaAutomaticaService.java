package com.manager.app.infinitepay;

import com.manager.app.financeiro.ContaReceber;
import com.manager.app.financeiro.ContaReceberCalculo;
import com.manager.app.financeiro.ContaReceberRecebimentoService;
import com.manager.app.financeiro.ContaReceberService;
import com.manager.app.financeiro.FormaPagamento;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// baixa automática pelo link de pagamento (fase 3B). O ERP BUSCA a fila do receptor na nuvem, guarda cada evento (payload bruto, idempotente por
// transaction_nsu + evento) e só então confirma (ack) ao receptor; depois processa: payment_check obrigatório, validação fechada e baixa pelo valor do título.
// Qualquer coisa inesperada vai para REVISAO; nunca se baixa por suposição. Tudo roda numa ÚNICA thread dedicada (sem usuário autenticado: a baixa fica com
// usuário vazio) e é serializado: o ciclo agendado e o botão manual nunca processam o mesmo evento duas vezes (além do UNIQUE e da trava consultiva por transação).
@Slf4j
@Service
public class BaixaAutomaticaService {

    static final int LOTE = 50;
    static final int MAX_RODADAS = 10;
    static final int MAX_TENTATIVAS = 10;
    static final int RETENCAO_DIAS = 90;
    static final int PAYLOAD_MAXIMO = 16000;
    static final Duration ESPERA_MANUAL = Duration.ofSeconds(30);

    private final ReceptorClient receptor;
    private final InfinitePayAutomaticoService automatico;
    private final InfinitePayConfiguracaoService configuracaoService;
    private final InfinitePayClient client;
    private final InfinitePayEventoRepository eventoRepository;
    private final ContaReceberLinkPagamentoRepository linkRepository;
    private final ContaReceberService contaReceberService;
    private final ContaReceberRecebimentoService recebimentoService;
    private final TravaAdvisory trava;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "infinitepay-fila");
        t.setDaemon(true);
        return t;
    });

    private volatile LocalDateTime ultimaExecucao;
    private volatile LocalDateTime ultimoSucesso;
    private volatile String ultimoErro;
    private volatile LocalDateTime ultimaLimpeza;
    private volatile int naoConfirmados;

    @Getter
    public static class Resumo {
        private int buscados;
        private int ingeridos;
        private int baixados;
        private int revisao;
        private int ignorados;
        private int erros;
        private java.math.BigDecimal valorBaixado = java.math.BigDecimal.ZERO;
        private final List<Long> recebimentos = new ArrayList<>();
        private String mensagem;
        private boolean emAndamento;
    }

    public record Estado(LocalDateTime ultimaExecucao, LocalDateTime ultimoSucesso, String ultimoErro, long aguardandoProcessamento, long emRevisao,
                         int itensNaoConfirmados) {
    }

    public BaixaAutomaticaService(ReceptorClient receptor, InfinitePayAutomaticoService automatico, InfinitePayConfiguracaoService configuracaoService,
                                  InfinitePayClient client, InfinitePayEventoRepository eventoRepository, ContaReceberLinkPagamentoRepository linkRepository,
                                  ContaReceberService contaReceberService, ContaReceberRecebimentoService recebimentoService, TravaAdvisory trava,
                                  ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.receptor = receptor;
        this.automatico = automatico;
        this.configuracaoService = configuracaoService;
        this.client = client;
        this.eventoRepository = eventoRepository;
        this.linkRepository = linkRepository;
        this.contaReceberService = contaReceberService;
        this.recebimentoService = recebimentoService;
        this.trava = trava;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public Estado estado() {
        return new Estado(ultimaExecucao, ultimoSucesso, ultimoErro,
                eventoRepository.countByStatus(InfinitePayEvento.Status.RECEBIDO) + eventoRepository.countByStatus(InfinitePayEvento.Status.ERRO_TEMPORARIO),
                eventoRepository.countByStatus(InfinitePayEvento.Status.REVISAO), naoConfirmados);
    }

    // executa uma tarefa na MESMA thread dedicada do ciclo (nunca em paralelo com ele) e devolve o resultado; erros de negócio são repassados como estão
    public <T> T serializado(java.util.concurrent.Callable<T> tarefa) {
        Future<T> f = executor.submit(tarefa);
        try {
            return f.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new ObjetoEmUsoException("Há uma verificação em andamento. Tente novamente em instantes.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ObjetoEmUsoException("A operação foi interrompida.");
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException("Falha ao executar a operação.", e.getCause());
        }
    }

    // ---------- entrada: ciclo agendado e botão manual ----------

    // espera o ciclo terminar (até 30 s); se já houver um em andamento, o pedido entra na fila da mesma thread (nunca em paralelo)
    public Resumo sincronizar() {
        Future<Resumo> f = executor.submit(this::ciclo);
        try {
            return f.get(ESPERA_MANUAL.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            Resumo r = new Resumo();
            r.emAndamento = true;
            r.mensagem = "Verificação em andamento. Tente novamente em instantes.";
            return r;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ObjetoEmUsoException("A verificação foi interrompida.");
        } catch (java.util.concurrent.ExecutionException e) {
            Resumo r = new Resumo();
            r.erros = 1;
            r.mensagem = "Não foi possível concluir a verificação. Tente novamente em instantes.";
            log.error("Falha no ciclo da fila de pagamentos: {}", e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName());
            return r;
        }
    }

    // ciclo agendado: não espera
    public void sincronizarEmSegundoPlano() {
        executor.submit(this::ciclo);
    }

    Resumo ciclo() {
        Resumo r = new Resumo();
        ultimaExecucao = LocalDateTime.now();
        int semConfirmar = 0;
        try {
            automatico.receptor(); // lança 409 se não configurado
        } catch (ObjetoEmUsoException e) {
            r.mensagem = "Receptor de pagamentos não configurado.";
            return r;
        }
        try {
            for (int rodada = 0; rodada < MAX_RODADAS; rodada++) {
                List<ReceptorClient.ItemFila> itens = receptor.buscarFila(LOTE);
                r.buscados += itens.size();
                List<Long> confirmar = new ArrayList<>();
                for (ReceptorClient.ItemFila item : itens) {
                    try {
                        if (ingerir(item)) r.ingeridos++;
                        confirmar.add(item.id());
                    } catch (RuntimeException e) {
                        // não confirma: o item volta no próximo ciclo
                        log.error("Falha ao guardar evento da fila (item {}): {}", item.id(), e.getClass().getSimpleName());
                        r.erros++;
                    }
                }
                semConfirmar += itens.size() - confirmar.size();
                if (!confirmar.isEmpty()) receptor.ack(confirmar);
                if (itens.size() < LOTE || confirmar.isEmpty()) break;
            }
            naoConfirmados = semConfirmar;
            if (automatico.baixaAutomaticaAtiva()) processarPendentes(r);
            limparReceptorSeNecessario();
            ultimoSucesso = LocalDateTime.now();
            ultimoErro = null;
            r.mensagem = r.baixados > 0 ? "Pagamento registrado." : "Nenhum pagamento novo.";
        } catch (InfinitePayApiException e) {
            ultimoErro = e.getMessage();
            r.erros++;
            r.mensagem = e.getMessage();
        } catch (RuntimeException e) {
            ultimoErro = "Falha inesperada ao processar a fila de pagamentos.";
            r.erros++;
            r.mensagem = ultimoErro;
            log.error("Falha inesperada no ciclo da fila de pagamentos", e);
        }
        return r;
    }

    // ---------- ingestão (idempotente) ----------

    // guarda o evento com o payload bruto; devolve true se for novo. Duplicado (mesmo transaction_nsu + evento) é só confirmado.
    boolean ingerir(ReceptorClient.ItemFila item) {
        String transacao = item.transactionNsu() == null || item.transactionNsu().isBlank() ? "item-" + item.id() : item.transactionNsu().trim();
        String evento = item.evento() == null || item.evento().isBlank() ? "desconhecido" : item.evento().trim().toLowerCase(java.util.Locale.ROOT);
        try {
            Boolean novo = tx.execute(status -> {
                if (eventoRepository.findByTransactionNsuAndEvento(transacao, evento).isPresent()) return false;
                InfinitePayEvento e = new InfinitePayEvento();
                e.setTransactionNsu(truncar(transacao, 120));
                e.setEvento(truncar(evento, 40));
                e.setOrderNsu(truncar(item.orderNsu(), 60));
                e.setPayload(truncar(item.corpo(), PAYLOAD_MAXIMO));
                e.setStatus(InfinitePayEvento.Status.RECEBIDO);
                eventoRepository.saveAndFlush(e);
                return true;
            });
            return Boolean.TRUE.equals(novo);
        } catch (DataIntegrityViolationException e) {
            return false; // corrida: outro ciclo gravou o mesmo evento
        }
    }

    // ---------- processamento ----------

    private void processarPendentes(Resumo r) {
        List<Long> ids = eventoRepository.findByStatusInOrderByIdAsc(List.of(InfinitePayEvento.Status.RECEBIDO, InfinitePayEvento.Status.ERRO_TEMPORARIO))
                .stream().map(InfinitePayEvento::getId).toList();
        for (Long id : ids) {
            try {
                processarEvento(id, r);
            } catch (RuntimeException e) {
                log.error("Falha ao processar o evento {}: {}", id, e.getClass().getSimpleName());
                r.erros++;
                marcar(id, InfinitePayEvento.Status.ERRO_TEMPORARIO, null, "Falha inesperada ao processar o evento. Será tentado novamente.", true);
            }
        }
    }

    // etapas: (1) leitura e classificação; (2) payment_check, sem transação; (3) decisão e baixa com o título bloqueado
    void processarEvento(Long id, Resumo r) {
        record Preparo(Long linkId, Long tituloId, BaixaAutomaticaCalculo.Payload payload) {
        }
        Preparo preparo = tx.execute(status -> {
            InfinitePayEvento ev = eventoRepository.findById(id).orElse(null);
            if (ev == null || !(ev.getStatus() == InfinitePayEvento.Status.RECEBIDO || ev.getStatus() == InfinitePayEvento.Status.ERRO_TEMPORARIO)) return null;
            BaixaAutomaticaCalculo.Payload p = BaixaAutomaticaCalculo.ler(ev.getPayload(), objectMapper);
            if (p.orderNsu() != null) ev.setOrderNsu(truncar(p.orderNsu(), 60));
            ev.setAmountCentavos(p.amount());
            ev.setPaidAmountCentavos(p.paidAmount());
            ev.setCaptureMethod(truncar(p.captureMethod(), 40));
            BaixaAutomaticaCalculo.Tipo tipo = BaixaAutomaticaCalculo.classificar(p);
            switch (tipo) {
                case INESPERADO -> {
                    concluir(ev, InfinitePayEvento.Status.REVISAO, MotivoRevisao.PAYLOAD_INESPERADO, "Payload inesperado ou incompleto" + (p.faltando().isEmpty() ? "" : " (campos ausentes ou inválidos: " + String.join(", ", p.faltando()) + ")")
                            + ". O payload bruto foi guardado para diagnóstico.");
                    r.revisao++;
                    return null;
                }
                case SINAL -> {
                    concluir(ev, InfinitePayEvento.Status.REVISAO, MotivoRevisao.EVENTO_INFORMATIVO, BaixaAutomaticaCalculo.motivoSinal(p.evento()));
                    r.revisao++;
                    return null;
                }
                case IGNORADO -> {
                    concluir(ev, InfinitePayEvento.Status.IGNORADO, null, "Evento informativo, sem ação.");
                    r.ignorados++;
                    return null;
                }
                default -> {
                }
            }
            if (!p.faltando().isEmpty()) {
                concluir(ev, InfinitePayEvento.Status.REVISAO, MotivoRevisao.PAYLOAD_INESPERADO, "Pagamento com campos ausentes ou inválidos: " + String.join(", ", p.faltando()) + ".");
                r.revisao++;
                return null;
            }
            Long linkId = linkRepository.findIdByOrderNsu(p.orderNsu()).orElse(null);
            if (linkId == null) {
                concluir(ev, InfinitePayEvento.Status.REVISAO, MotivoRevisao.LINK_DESCONHECIDO, "Link de pagamento desconhecido (order_nsu " + p.orderNsu() + ").");
                r.revisao++;
                return null;
            }
            ContaReceberLinkPagamento link = linkRepository.findById(linkId).orElseThrow();
            ev.setLink(link);
            ev.setTentativas(ev.getTentativas() + 1);
            eventoRepository.save(ev);
            return new Preparo(linkId, link.getContaReceber().getId(), p);
        });
        if (preparo == null) return;
        BaixaAutomaticaCalculo.Payload p = preparo.payload();

        InfinitePayClient.PagamentoVerificado check;
        try {
            InfinitePayConfiguracaoService.Credenciais c = configuracaoService.credenciais();
            check = client.verificarPagamento(c.chave(), c.handle(), p.orderNsu(), p.transactionNsu(), p.slug());
        } catch (InfinitePayApiException | ObjetoEmUsoException e) {
            r.erros++;
            Integer tentativas = tx.execute(s -> eventoRepository.findById(id).map(InfinitePayEvento::getTentativas).orElse(0));
            if (tentativas != null && tentativas >= MAX_TENTATIVAS) {
                marcar(id, InfinitePayEvento.Status.REVISAO, MotivoRevisao.CONFIRMACAO_FALHOU, "Não foi possível confirmar o pagamento na InfinitePay após " + MAX_TENTATIVAS + " tentativas: " + e.getMessage(), false);
                r.revisao++;
            } else {
                marcar(id, InfinitePayEvento.Status.ERRO_TEMPORARIO, null, "Confirmação pendente: " + e.getMessage(), false);
            }
            return;
        }
        final InfinitePayClient.PagamentoVerificado verificado = check;
        try {
            tx.executeWithoutResult(status -> decidirEBaixar(id, preparo.linkId(), preparo.tituloId(), p, verificado, r));
        } catch (RuntimeException e) {
            // nada foi gravado (transação desfeita): vai para revisão com o motivo, sem perder o evento
            log.warn("Baixa automática do evento {} não pôde ser registrada: {}", id, e.getClass().getSimpleName());
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            marcar(id, InfinitePayEvento.Status.REVISAO, MotivoRevisao.ERRO_AO_BAIXAR, "Não foi possível registrar a baixa automática: " + msg, false);
            r.revisao++;
        }
    }

    private void decidirEBaixar(Long id, Long linkId, Long tituloId, BaixaAutomaticaCalculo.Payload p, InfinitePayClient.PagamentoVerificado check, Resumo r) {
        ContaReceber titulo = contaReceberService.buscarComLock(tituloId); // ordem de travas: título primeiro
        trava.travar(p.transactionNsu() + "|" + (p.evento() == null ? "" : p.evento()));
        InfinitePayEvento ev = eventoRepository.findById(id).orElseThrow();
        if (!(ev.getStatus() == InfinitePayEvento.Status.RECEBIDO || ev.getStatus() == InfinitePayEvento.Status.ERRO_TEMPORARIO)) return;
        ContaReceberLinkPagamento link = linkRepository.findById(linkId).orElseThrow();
        ev.setAmountCentavos(check.amount());
        ev.setPaidAmountCentavos(check.paidAmount());
        String captura = check.captureMethod() != null ? check.captureMethod() : p.captureMethod();
        ev.setCaptureMethod(truncar(captura, 40));
        FormaPagamento forma = automatico.formaPorCaptura(captura);

        BaixaAutomaticaCalculo.Verificacao v = new BaixaAutomaticaCalculo.Verificacao(check.success(), check.paid(), check.amount(), check.paidAmount());
        BaixaAutomaticaCalculo.Decisao d;
        if (check.success() && check.paid() && p.amount() != null && p.amount() != check.amount()) {
            d = new BaixaAutomaticaCalculo.Decisao(BaixaAutomaticaCalculo.Acao.REVISAO, MotivoRevisao.VALOR_DIVERGENTE,
                    "O valor informado no webhook (" + BaixaAutomaticaCalculo.moeda(p.amount()) + ") diverge do confirmado pela InfinitePay (" + BaixaAutomaticaCalculo.moeda(check.amount()) + ").",
                    java.math.BigDecimal.ZERO);
        } else {
            d = BaixaAutomaticaCalculo.decidir(new BaixaAutomaticaCalculo.LinkInfo(link.getStatus(), link.getModo(), link.getValor(), link.getTransactionNsu(),
                            link.getBanco() != null && link.getBanco().isAtivo()),
                    new BaixaAutomaticaCalculo.TituloInfo(titulo.getSituacao(), ContaReceberCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorRecebido())),
                    v, p.transactionNsu(), forma != null);
        }
        switch (d.acao()) {
            case BAIXAR -> {
                var rec = recebimentoService.receberAutomatico(titulo, link.getBanco().getId(), forma, LocalDate.now(LinkPagamentoCalculo.FUSO), link.getValor(), d.taxaCliente(),
                        BaixaAutomaticaCalculo.observacao(new BaixaAutomaticaCalculo.Payload(true, p.evento(), p.orderNsu(), p.transactionNsu(), p.slug(), check.amount(),
                                check.paidAmount(), captura, check.installments() != null ? check.installments() : p.installments(), List.of()), d.taxaCliente()),
                        link, p.transactionNsu());
                ev.setRecebimentoId(rec.getId());
                concluir(ev, InfinitePayEvento.Status.PROCESSADO, null, "Baixa automática registrada (recebimento nº " + rec.getId() + ")."
                        + (d.taxaCliente().signum() > 0 ? " Taxa paga pelo cliente: " + BaixaAutomaticaCalculo.moeda(BaixaAutomaticaCalculo.centavos(d.taxaCliente())) + "." : ""));
                r.baixados++;
                r.valorBaixado = r.valorBaixado.add(link.getValor());
                r.recebimentos.add(rec.getId());
            }
            case REVISAO -> {
                concluir(ev, InfinitePayEvento.Status.REVISAO, d.codigo(), d.motivo());
                r.revisao++;
            }
            default -> {
                concluir(ev, InfinitePayEvento.Status.IGNORADO, null, d.motivo());
                r.ignorados++;
            }
        }
    }

    // ---------- retenção no receptor ----------

    // a cada 24 h pede ao receptor que apague itens JÁ CONFIRMADOS (ack) há mais de 90 dias; nunca itens sem ack. Falha aqui não interrompe nada.
    private void limparReceptorSeNecessario() {
        LocalDateTime ultima = ultimaLimpeza;
        if (ultima != null && ultima.isAfter(LocalDateTime.now().minusHours(24))) return;
        try {
            int apagados = receptor.limpar(RETENCAO_DIAS);
            ultimaLimpeza = LocalDateTime.now();
            if (apagados > 0) log.info("Receptor de pagamentos: {} item(ns) confirmados há mais de {} dias apagados.", apagados, RETENCAO_DIAS);
        } catch (RuntimeException e) {
            log.warn("Não foi possível pedir a limpeza de retenção ao receptor: {}", e.getClass().getSimpleName());
        }
    }

    // ---------- utilidades ----------

    private void concluir(InfinitePayEvento ev, InfinitePayEvento.Status status, MotivoRevisao codigo, String motivo) {
        ev.setStatus(status);
        ev.setMotivoCodigo(codigo == null ? null : codigo.name());
        ev.setMotivo(truncar(motivo, 500));
        ev.setProcessadoEm(LocalDateTime.now());
        eventoRepository.save(ev);
    }

    // atualização isolada (nova transação) do estado de um evento
    private void marcar(Long id, InfinitePayEvento.Status status, MotivoRevisao codigo, String motivo, boolean contarTentativa) {
        try {
            tx.executeWithoutResult(s -> eventoRepository.findById(id).ifPresent(ev -> {
                if (contarTentativa) ev.setTentativas(ev.getTentativas() + 1);
                ev.setStatus(status);
                ev.setMotivoCodigo(codigo == null ? null : codigo.name());
                ev.setMotivo(truncar(motivo, 500));
                if (status != InfinitePayEvento.Status.ERRO_TEMPORARIO) ev.setProcessadoEm(LocalDateTime.now());
                eventoRepository.save(ev);
            }));
        } catch (RuntimeException e) {
            log.error("Não foi possível atualizar o evento {}: {}", id, e.getClass().getSimpleName());
        }
    }

    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
