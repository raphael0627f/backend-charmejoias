package com.manager.app.infinitepay;

import com.manager.app.financeiro.ContaReceber;
import com.manager.app.financeiro.ContaReceberEvento;
import com.manager.app.financeiro.ContaReceberEventoRepository;
import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// transições de estado dos links ligadas ao título (quitação, cancelamento, baixa pelo link). Usado pelos serviços do Contas a Receber
// dentro da transação deles, com o título já bloqueado; não depende do serviço de links (evita dependência circular).
@Component
@RequiredArgsConstructor
public class LinkPagamentoCicloVida {

    public static final String AVISO_CANCELAMENTO = "Os links de pagamento já enviados não podem ser cancelados na InfinitePay e ainda podem ser pagos.";

    private final ContaReceberLinkPagamentoRepository repository;
    private final ContaReceberEventoRepository eventoRepository;
    private final UsuarioAuditorAware auditor;

    // título quitado: links ativos viram ENCERRADO_TITULO_RECEBIDO
    public void aoQuitar(ContaReceber titulo) {
        if (titulo.getSituacao() != SituacaoContaReceber.RECEBIDO) return;
        mudar(repository.findByContaReceberIdAndStatus(titulo.getId(), StatusLinkPagamento.ATIVO), StatusLinkPagamento.ENCERRADO_TITULO_RECEBIDO);
    }

    // título cancelado: links ativos viram TITULO_CANCELADO; devolve true quando havia link ativo (a resposta traz o aviso formal)
    public boolean aoCancelar(ContaReceber titulo) {
        List<ContaReceberLinkPagamento> ativos = repository.findByContaReceberIdAndStatus(titulo.getId(), StatusLinkPagamento.ATIVO);
        mudar(ativos, StatusLinkPagamento.TITULO_CANCELADO);
        return !ativos.isEmpty();
    }

    // linkId do recebimento manual: o link deve ser do título e estar ativo
    public ContaReceberLinkPagamento validarParaRecebimento(Long tituloId, Long linkId) {
        ContaReceberLinkPagamento link = repository.findById(linkId)
                .orElseThrow(() -> new ItemInvalidoException("O link de pagamento informado não foi encontrado."));
        if (!link.getContaReceber().getId().equals(tituloId)) {
            throw new ItemInvalidoException("O link de pagamento informado não pertence a este título.");
        }
        if (link.getStatus() != StatusLinkPagamento.ATIVO) {
            throw new ItemInvalidoException("O link de pagamento informado não está ativo.");
        }
        return link;
    }

    // marca o link como pago manualmente (com ou sem recebimento) e marca os demais ativos do título como NAO_UTILIZAR
    public void marcarPago(ContaReceberLinkPagamento link, Long recebimentoId, String observacao) {
        Long tituloId = link.getContaReceber().getId();
        link.setStatus(StatusLinkPagamento.PAGO_MANUAL);
        link.setRecebimentoId(recebimentoId);
        link.setAtualizadoEm(LocalDateTime.now());
        repository.save(link);
        List<ContaReceberLinkPagamento> demais = repository.findByContaReceberIdAndStatus(tituloId, StatusLinkPagamento.ATIVO).stream()
                .filter(l -> !l.getId().equals(link.getId())).toList();
        mudar(demais, StatusLinkPagamento.NAO_UTILIZAR);
        String detalhe = "Link de pagamento nº " + link.getId() + " marcado como pago manualmente"
                + (recebimentoId != null ? " (recebimento nº " + recebimentoId + ")" : "") + "."
                + (demais.isEmpty() ? "" : " Os demais links ativos do título foram marcados como \"Não utilizar\".")
                + (observacao != null && !observacao.isBlank() ? " Observação: " + observacao.trim() : "");
        registrar(link.getContaReceber(), "LINK_PAGAMENTO_MARCADO_PAGO", detalhe);
    }

    public void registrar(ContaReceber titulo, String evento, String detalhe) {
        ContaReceberEvento e = new ContaReceberEvento();
        e.setContaReceber(titulo);
        e.setEvento(evento);
        e.setDetalhe(detalhe != null && detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe);
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        e.setUsuario(usuario);
        e.setDataHora(LocalDateTime.now());
        eventoRepository.save(e);
    }

    private void mudar(List<ContaReceberLinkPagamento> links, StatusLinkPagamento novo) {
        LocalDateTime agora = LocalDateTime.now();
        for (ContaReceberLinkPagamento l : links) {
            l.setStatus(novo);
            l.setAtualizadoEm(agora);
        }
        if (!links.isEmpty()) repository.saveAll(links);
    }
}
