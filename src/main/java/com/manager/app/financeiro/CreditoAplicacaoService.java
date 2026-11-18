package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CreditoClienteDTOs;
import com.manager.app.financeiro.dto.RecebimentoDTOs;
import com.manager.app.infinitepay.LinkPagamentoCicloVida;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// aplicação do crédito do cliente em título a receber: recebimento SEM banco (origem CREDITO_CLIENTE, sem movimentacao_banco, fora do caixa).
// Ordem de trava: título -> lotes ativos do cliente (por id) -> excedente (via ExcedenteAcertoService). Consumo FIFO dos lotes.
@Service
@RequiredArgsConstructor
public class CreditoAplicacaoService {

    private final ContaReceberService contaReceberService;
    private final ContaReceberRepository contaReceberRepository;
    private final ContaReceberRecebimentoRepository recebimentoRepository;
    private final ContaReceberEventoRepository eventoRepository;
    private final CreditoClienteLoteRepository loteRepository;
    private final CreditoClienteMovimentoRepository movimentoRepository;
    private final CreditoClienteService creditoService;
    private final ExcedenteAcertoService excedenteService;
    private final LinkPagamentoCicloVida linkCicloVida;
    private final ContaReceberMapper mapper;
    private final UsuarioAuditorAware auditor;

    @Transactional
    public RecebimentoDTOs.Resultado aplicar(Long tituloId, CreditoClienteDTOs.AplicarRequest r) {
        TextoUtils.validarTexto(r == null ? null : r.observacao(), "A observação", 500);
        ContaReceber titulo = contaReceberService.buscarComLock(tituloId);
        RecebimentoCalculo.validarTitulo(titulo);
        if (titulo.getCliente() == null) throw new ItemInvalidoException("Somente título de cliente cadastrado pode receber crédito.");
        List<CreditoClienteLote> lotes = loteRepository.findAtivosForUpdate(titulo.getCliente().getId()).stream().filter(l -> l.getDisponivel().signum() > 0).toList();
        BigDecimal saldo = BigDecimal.ZERO;
        for (CreditoClienteLote l : lotes) saldo = saldo.add(l.getDisponivel());
        saldo = CalculoFinanceiro.arredondar(saldo);
        if (saldo.signum() <= 0) throw new ItemInvalidoException("O cliente não possui crédito disponível.");
        BigDecimal aberto = CalculoFinanceiro.arredondar(ContaReceberCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorRecebido()));
        if (aberto.signum() <= 0) throw new ItemInvalidoException("O título não possui valor em aberto.");
        BigDecimal maximo = saldo.min(aberto);
        BigDecimal valor = r == null || r.valor() == null ? maximo : CalculoFinanceiro.validarValorPositivo(r.valor());
        if (valor.compareTo(maximo) > 0) {
            throw new ItemInvalidoException("O valor aplicado não pode ser superior ao menor entre o crédito disponível (" + MovimentacaoBancoService.moeda(saldo)
                    + ") e o valor em aberto do título (" + MovimentacaoBancoService.moeda(aberto) + ").");
        }
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);

        ContaReceberRecebimento rec = new ContaReceberRecebimento();
        rec.setContaReceber(titulo);
        rec.setOrigem(ContaReceberRecebimento.Origem.CREDITO_CLIENTE);
        rec.setDataRecebimento(LocalDate.now());
        rec.setValorPrincipal(valor);
        rec.setValorTotal(valor);
        rec.setObservacao(r == null || r.observacao() == null || r.observacao().isBlank() ? null : r.observacao().trim());
        rec = recebimentoRepository.save(rec);

        // FIFO nos lotes
        BigDecimal restante = valor;
        for (CreditoClienteLote lote : lotes) {
            if (restante.signum() <= 0) break;
            BigDecimal uso = restante.min(lote.getDisponivel());
            lote.setValorUtilizado(CalculoFinanceiro.arredondar(lote.getValorUtilizado()).add(uso));
            lote.setUpdatedAt(LocalDateTime.now());
            loteRepository.save(lote);
            creditoService.movimento(lote, CreditoClienteMovimento.Tipo.SAIDA, uso, "Aplicação no título nº " + titulo.getNumero() + ".", usuario, titulo.getId(), rec.getId());
            restante = restante.subtract(uso);
        }
        titulo.setValorRecebido(CalculoFinanceiro.arredondar(titulo.getValorRecebido()).add(valor));
        titulo.setSituacao(ContaReceberCalculo.situacaoPara(titulo.getValor(), titulo.getValorRecebido()));
        contaReceberRepository.save(titulo);
        registrar(titulo, "CREDITO_APLICADO", "Crédito do cliente aplicado: " + MovimentacaoBancoService.moeda(valor) + " (recebimento nº " + rec.getId()
                + ", sem movimentação bancária). Saldo de crédito restante: " + MovimentacaoBancoService.moeda(saldo.subtract(valor)) + ".", usuario);
        // links ativos do título deixam de valer (a InfinitePay cobraria o valor antigo)
        boolean comLink = linkCicloVida.aoAjustarValor(titulo);
        if (comLink) registrar(titulo, "LINK_PAGAMENTO_NAO_UTILIZAR", "Links de pagamento ativos marcados como não utilizar: crédito do cliente aplicado ao título.", usuario);
        excedenteService.sincronizarPorTitulo(titulo);
        return new RecebimentoDTOs.Resultado(mapper.recebimento(rec, null), contaReceberService.detalhe(titulo), comLink ? LinkPagamentoCicloVida.avisoDoLote(1) : null);
    }

    // trava os lotes ativos do cliente (chamado ANTES de qualquer trava de excedente, para manter a ordem título -> lotes -> excedente)
    public void travarLotes(UUID clienteId) {
        loteRepository.findAtivosForUpdate(clienteId);
    }

    // estorno da aplicação: devolve o crédito aos lotes de origem (ESTORNO_SAIDA) e reabre o saldo do título. Recebimento e título já estão bloqueados.
    @Transactional
    public void estornar(ContaReceberRecebimento recebimento, String motivo, Usuario usuario) {
        ContaReceber titulo = recebimento.getContaReceber();
        RecebimentoCalculo.estornarSemBanco(recebimento, motivo, usuario, LocalDateTime.now());
        for (CreditoClienteMovimento saida : movimentoRepository.findByRecebimentoIdAndTipo(recebimento.getId(), CreditoClienteMovimento.Tipo.SAIDA)) {
            CreditoClienteLote lote = loteRepository.findByIdForUpdate(saida.getLote().getId()).orElseThrow();
            lote.setValorUtilizado(CalculoFinanceiro.arredondar(lote.getValorUtilizado()).subtract(saida.getValor()).max(BigDecimal.ZERO));
            lote.setUpdatedAt(LocalDateTime.now());
            loteRepository.save(lote);
            creditoService.movimento(lote, CreditoClienteMovimento.Tipo.ESTORNO_SAIDA, saida.getValor(),
                    "Estorno da aplicação no título nº " + titulo.getNumero() + ". Motivo: " + motivo, usuario, titulo.getId(), recebimento.getId());
        }
    }

    private void registrar(ContaReceber titulo, String evento, String detalhe, Usuario usuario) {
        ContaReceberEvento e = new ContaReceberEvento();
        e.setContaReceber(titulo);
        e.setEvento(evento);
        e.setDetalhe(detalhe != null && detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe);
        e.setUsuario(usuario);
        e.setDataHora(LocalDateTime.now());
        eventoRepository.save(e);
    }
}
