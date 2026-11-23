package com.manager.app.infinitepay;

import com.manager.app.financeiro.ContaReceber;
import com.manager.app.financeiro.ContaReceberService;
import com.manager.app.infinitepay.dto.RevisaoDTOs;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.LimiteExcedidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.usuario.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// botão "Verificar pagamento" (título, pré-acerto e acerto) e alertas internos da baixa automática.
// Verificar = mesma rotina e mesma trava do ciclo agendado (busca a fila e processa); a InfinitePay não permite consultar por order_nsu, então
// "ainda não pago" significa que nenhum aviso do pagamento chegou ao receptor. Limite: 1 chamada a cada 10 s por usuário.
@Service
public class VerificarPagamentoService {

    static final long INTERVALO_MS = 10_000;

    private final BaixaAutomaticaService baixa;
    private final InfinitePayAutomaticoService automatico;
    private final ContaReceberService contaReceberService;
    private final ContaReceberLinkPagamentoRepository links;
    private final InfinitePayEventoRepository eventos;
    private final UsuarioAuditorAware auditor;
    private final TransactionTemplate txLeitura;
    private final Map<UUID, Long> ultimaChamada = new ConcurrentHashMap<>();

    public VerificarPagamentoService(BaixaAutomaticaService baixa, InfinitePayAutomaticoService automatico, ContaReceberService contaReceberService,
                                     ContaReceberLinkPagamentoRepository links, InfinitePayEventoRepository eventos, UsuarioAuditorAware auditor,
                                     PlatformTransactionManager tm) {
        this.baixa = baixa;
        this.automatico = automatico;
        this.contaReceberService = contaReceberService;
        this.links = links;
        this.eventos = eventos;
        this.auditor = auditor;
        this.txLeitura = new TransactionTemplate(tm);
        this.txLeitura.setReadOnly(true);
    }

    public RevisaoDTOs.VerificarResposta verificar(Long tituloId) {
        if (tituloId == null) throw new ItemInvalidoException("Informe o título.");
        limitar();
        boolean temLinkAtivo = Boolean.TRUE.equals(txLeitura.execute(s -> links.findByContaReceberIdAndStatus(contaReceberService.buscar(tituloId).getId(), StatusLinkPagamento.ATIVO)
                .stream().anyMatch(l -> l.getModo() == ModoLinkPagamento.AUTOMATICO)));
        if (!temLinkAtivo) throw new ItemInvalidoException("Este título não possui link de pagamento automático ativo.");
        if (!automatico.baixaAutomaticaAtiva()) throw new ObjetoEmUsoException("A baixa automática está desligada em Sistema > InfinitePay.");
        BaixaAutomaticaService.Resumo r = baixa.sincronizar();
        return txLeitura.execute(s -> resposta(tituloId, r));
    }

    private RevisaoDTOs.VerificarResposta resposta(Long tituloId, BaixaAutomaticaService.Resumo r) {
        ContaReceber titulo = contaReceberService.buscar(tituloId);
        List<ContaReceberLinkPagamento> todos = links.findByContaReceberIdOrderByIdDesc(tituloId);
        ContaReceberLinkPagamento pago = todos.stream().filter(l -> l.getStatus() == StatusLinkPagamento.PAGO_AUTOMATICO && l.getRecebimentoId() != null).findFirst().orElse(null);
        Object detalhe = contaReceberService.detalhe(titulo);
        if (pago != null) return new RevisaoDTOs.VerificarResposta("PAGAMENTO_REGISTRADO", "Pagamento confirmado e registrado.", detalhe, pago.getRecebimentoId());
        List<Long> ids = todos.stream().map(ContaReceberLinkPagamento::getId).toList();
        boolean emRevisao = !ids.isEmpty() && eventos.findByLinkIdInOrderByIdDesc(ids).stream()
                .anyMatch(e -> e.getStatus() == InfinitePayEvento.Status.REVISAO && e.getResolvidoEm() == null);
        if (emRevisao) return new RevisaoDTOs.VerificarResposta("EM_REVISAO", "Há um pagamento deste título aguardando revisão do administrador.", detalhe, null);
        if (r.isEmAndamento()) return new RevisaoDTOs.VerificarResposta("EM_ANDAMENTO", "Verificação em andamento. Tente novamente em instantes.", detalhe, null);
        if (r.getErros() > 0) return new RevisaoDTOs.VerificarResposta("INDISPONIVEL", r.getMensagem(), detalhe, null);
        return new RevisaoDTOs.VerificarResposta("AINDA_NAO_PAGO", "Pagamento ainda não identificado.", detalhe, null);
    }

    private void limitar() {
        Usuario u = auditor.getCurrentAuditor().orElse(null);
        if (u == null) return;
        long agora = System.currentTimeMillis();
        if (ultimaChamada.size() > 1000) ultimaChamada.values().removeIf(t -> agora - t > INTERVALO_MS);
        boolean[] bloqueado = {false};
        ultimaChamada.compute(u.getId(), (k, anterior) -> {
            if (anterior != null && agora - anterior < INTERVALO_MS) {
                bloqueado[0] = true;
                return anterior;
            }
            return agora;
        });
        if (bloqueado[0]) throw new LimiteExcedidoException("Aguarde alguns segundos para verificar novamente.");
    }

    // ---------- alertas internos (sem e-mail) ----------

    public RevisaoDTOs.Alertas alertas() {
        List<RevisaoDTOs.Alerta> a = new java.util.ArrayList<>();
        BaixaAutomaticaService.Estado e = baixa.estado();
        if (e.emRevisao() > 0) {
            a.add(new RevisaoDTOs.Alerta("REVISOES_PENDENTES", "ALERTA", e.emRevisao() == 1 ? "Há 1 pagamento da InfinitePay aguardando revisão." : "Há " + e.emRevisao() + " pagamentos da InfinitePay aguardando revisão.",
                    e.emRevisao(), ROTA_REVISAO));
        }
        long erros = eventos.countByStatus(InfinitePayEvento.Status.ERRO_TEMPORARIO);
        if (erros > 0) a.add(new RevisaoDTOs.Alerta("FILA_COM_ERRO", "ALERTA", "Há " + erros + " pagamento(s) com confirmação pendente; o sistema tenta novamente sozinho.", erros, ROTA_REVISAO));
        if (e.ultimoErro() != null) a.add(new RevisaoDTOs.Alerta("RECEPTOR_INDISPONIVEL", "ERRO", "A última verificação de pagamentos falhou: " + e.ultimoErro(), 1, ROTA_CONFIG));
        if (e.itensNaoConfirmados() > 0) {
            a.add(new RevisaoDTOs.Alerta("ITENS_SEM_CONFIRMACAO", "ERRO", "Há " + e.itensNaoConfirmados() + " aviso(s) de pagamento que não puderam ser guardados e seguem no receptor.", e.itensNaoConfirmados(), ROTA_CONFIG));
        }
        long aguardando = eventos.countByStatus(InfinitePayEvento.Status.RECEBIDO);
        if (aguardando > 0 && !automatico.baixaAutomaticaAtiva()) {
            a.add(new RevisaoDTOs.Alerta("AGUARDANDO_COM_INTERRUPTOR_DESLIGADO", "ALERTA", "Há " + aguardando + " pagamento(s) recebido(s) e a baixa automática está desligada.", aguardando, ROTA_CONFIG));
        }
        return new RevisaoDTOs.Alertas(a.size(), a);
    }

    static final String ROTA_REVISAO = "/contas-receber/revisao-infinitepay";
    static final String ROTA_CONFIG = "/sistema/infinitepay";
}
