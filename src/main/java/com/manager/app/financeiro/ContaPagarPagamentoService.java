package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.BaixaDTOs;
import com.manager.app.financeiro.dto.ContaPagarDetalheDTO;
import com.manager.app.financeiro.dto.EstornoRequestDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

// baixa, estorno de baixa e baixa em lote. Concorrência: bloqueio pessimista do título e depois do banco (sempre nessa ordem),
// para que duas baixas simultâneas nunca paguem o mesmo saldo duas vezes. Regras e cálculos em BaixaCalculo.
@Service
@RequiredArgsConstructor
public class ContaPagarBaixaService {

    static final int LOTE_MAXIMO = 200;

    private final ContaPagarService contaPagarService;
    private final ContaPagarRepository contaPagarRepository;
    private final ContaPagarBaixaRepository baixaRepository;
    private final ContaPagarEventoRepository eventoRepository;
    private final MovimentacaoBancoRepository movimentacaoRepository;
    private final BancoRepository bancoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final MovimentacaoBancoService movimentacaoService;
    private final ContaPagarMapper mapper;
    private final UsuarioAuditorAware auditor;
    private final PlatformTransactionManager transactionManager;

    private record Aplicada(ContaPagarBaixa baixa, MovimentacaoBanco movimentacao) {
    }

    // ---------- baixa individual ----------

    @Transactional
    public BaixaDTOs.Resultado baixar(Long tituloId, BaixaDTOs.Request r) {
        TextoUtils.validarTexto(r.observacao(), "A observação", 500);
        ContaPagar titulo = contaPagarService.buscarComLock(tituloId);
        Banco banco = bancoRepository.findByIdForUpdate(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        FormaPagamento forma = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        Aplicada a = aplicar(titulo, banco, forma, r.dataPagamento(), r.valorPrincipal(), r.juros(), r.multa(), r.desconto(), r.observacao());
        return new BaixaDTOs.Resultado(mapper.baixa(a.baixa(), a.movimentacao().getId()), contaPagarService.detalhe(titulo),
                movimentacaoService.avisoSaldoNegativo(List.of(banco)));
    }

    private Aplicada aplicar(ContaPagar titulo, Banco banco, FormaPagamento forma, LocalDate data, BigDecimal principal, BigDecimal juros,
                             BigDecimal multa, BigDecimal desconto, String observacao) {
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        BaixaCalculo.ResultadoBaixa r = BaixaCalculo.baixar(titulo, banco, forma, data, principal, juros, multa, desconto, observacao, usuario, LocalDate.now());
        ContaPagarBaixa baixa = baixaRepository.save(r.baixa());
        MovimentacaoBanco mov = r.movimentacao();
        mov.setBaixaId(baixa.getId());
        movimentacaoRepository.save(mov);
        contaPagarRepository.save(titulo);
        registrar(titulo, "BAIXA", "Baixa de " + MovimentacaoBancoService.moeda(baixa.getValorTotal()) + " em " + TextoUtils.data(data)
                + " (principal " + MovimentacaoBancoService.moeda(baixa.getValorPrincipal()) + ", juros " + MovimentacaoBancoService.moeda(baixa.getJuros())
                + ", multa " + MovimentacaoBancoService.moeda(baixa.getMulta()) + ", desconto " + MovimentacaoBancoService.moeda(baixa.getDesconto())
                + "), banco \"" + banco.getNome() + "\", forma \"" + forma.getNome() + "\".", usuario);
        return new Aplicada(baixa, mov);
    }

    // ---------- estorno ----------

    @Transactional
    public BaixaDTOs.Resultado estornar(Long baixaId, EstornoRequestDTO request) {
        String motivo = CalculoFinanceiro.validarMotivo(request == null ? null : request.motivo());
        Long tituloId = baixaRepository.findContaPagarIdById(baixaId).orElseThrow(() -> new ObjetoNaoEncontradoException("Baixa não encontrada."));
        ContaPagar titulo = contaPagarService.buscarComLock(tituloId);
        ContaPagarBaixa baixa = baixaRepository.findByIdForUpdate(baixaId).orElseThrow(() -> new ObjetoNaoEncontradoException("Baixa não encontrada."));
        if (baixa.isEstornada()) throw new ItemInvalidoException("Esta baixa já foi estornada.");
        MovimentacaoBanco original = movimentacaoRepository.findByBaixaIdAndOrigem(baixaId, OrigemMovimentacao.BAIXA_CONTA_PAGAR).stream().findFirst()
                .orElseThrow(() -> new ItemInvalidoException("A movimentação bancária desta baixa não foi localizada."));
        bancoRepository.findByIdForUpdate(original.getBanco().getId());

        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        MovimentacaoBanco inversa = BaixaCalculo.estornar(baixa, original, motivo, usuario, LocalDate.now(), LocalDateTime.now());
        movimentacaoRepository.save(inversa);
        baixaRepository.save(baixa);
        contaPagarRepository.save(titulo);
        registrar(titulo, "ESTORNO_BAIXA", "Estorno da baixa nº " + baixa.getId() + " no valor total de " + MovimentacaoBancoService.moeda(baixa.getValorTotal())
                + " (principal " + MovimentacaoBancoService.moeda(baixa.getValorPrincipal()) + "), realizada em " + TextoUtils.data(baixa.getDataPagamento())
                + ". Motivo: " + motivo, usuario);
        return new BaixaDTOs.Resultado(mapper.baixa(baixa, original.getId()), contaPagarService.detalhe(titulo), null);
    }

    // ---------- baixa em lote ----------

    // paga o saldo integral de cada título, cada um em transação própria; um título recusado nunca derruba o lote
    public BaixaDTOs.ResultadoLote baixarLote(BaixaDTOs.Lote r) {
        if (r == null || r.ids() == null || r.ids().isEmpty()) throw new ItemInvalidoException("Selecione ao menos um título.");
        if (r.ids().stream().anyMatch(Objects::isNull)) throw new ItemInvalidoException("A lista de títulos contém um identificador inválido.");
        if (r.ids().size() > LOTE_MAXIMO) throw new ItemInvalidoException("A baixa em lote aceita no máximo " + LOTE_MAXIMO + " títulos.");
        if (r.dataPagamento() == null) throw new ItemInvalidoException("A data do pagamento é obrigatória.");
        if (r.bancoId() == null) throw new ItemInvalidoException("O banco é obrigatório.");
        if (r.formaPagamentoId() == null) throw new ItemInvalidoException("A forma de pagamento é obrigatória.");
        TextoUtils.validarTexto(r.observacao(), "A observação", 500);
        CalculoFinanceiro.validarDataNaoFutura(r.dataPagamento(), LocalDate.now());
        Banco bancoInicial = bancoRepository.findById(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        CalculoFinanceiro.validarBancoAtivo(bancoInicial);
        FormaPagamento formaInicial = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        if (!formaInicial.isAtivo()) throw new ItemInvalidoException("A forma de pagamento \"" + formaInicial.getNome() + "\" está inativa.");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<BaixaDTOs.ItemLote> itens = new ArrayList<>();
        BigDecimal totalPago = BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        int baixados = 0;
        for (Long id : new LinkedHashSet<>(r.ids())) {
            try {
                BaixaDTOs.ItemLote item = tx.execute(status -> baixarIntegral(id, r));
                itens.add(item);
                baixados++;
                totalPago = totalPago.add(item.valorBaixado());
            } catch (ItemInvalidoException | ObjetoNaoEncontradoException e) {
                itens.add(new BaixaDTOs.ItemLote(id, numeroOuNulo(id), false, null, null, null, null, e.getMessage()));
            }
        }
        String aviso = baixados == 0 ? null : movimentacaoService.avisoSaldoNegativo(List.of(bancoInicial));
        return new BaixaDTOs.ResultadoLote(baixados, itens.size() - baixados, totalPago, itens, aviso);
    }

    private BaixaDTOs.ItemLote baixarIntegral(Long id, BaixaDTOs.Lote r) {
        ContaPagar titulo = contaPagarService.buscarComLock(id);
        Banco banco = bancoRepository.findByIdForUpdate(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        FormaPagamento forma = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        BigDecimal saldo = BaixaCalculo.saldoIntegral(titulo);
        Aplicada a = aplicar(titulo, banco, forma, r.dataPagamento(), saldo, null, null, null, r.observacao());
        return new BaixaDTOs.ItemLote(id, titulo.getNumero(), true, a.baixa().getId(), a.baixa().getValorTotal(),
                ContaPagarCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorPago()), titulo.getSituacao().name(), null);
    }

    private Long numeroOuNulo(Long id) {
        return contaPagarRepository.findById(id).map(ContaPagar::getNumero).orElse(null);
    }

    private void registrar(ContaPagar titulo, String evento, String detalhe, Usuario usuario) {
        ContaPagarEvento e = new ContaPagarEvento();
        e.setContaPagar(titulo);
        e.setEvento(evento);
        e.setDetalhe(detalhe != null && detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe);
        e.setUsuario(usuario);
        e.setDataHora(LocalDateTime.now());
        eventoRepository.save(e);
    }
}
