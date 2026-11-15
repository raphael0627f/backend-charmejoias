package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.PagamentoDTOs;
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

// pagamento, estorno de pagamento e pagamento em lote. Concorrência: bloqueio pessimista do título e depois do banco (sempre nessa ordem),
// para que dois pagamentos simultâneos nunca paguem o mesmo saldo duas vezes. Regras e cálculos em PagamentoCalculo.
@Service
@RequiredArgsConstructor
public class ContaPagarPagamentoService {

    static final int LOTE_MAXIMO = 200;

    private final ContaPagarService contaPagarService;
    private final ContaPagarRepository contaPagarRepository;
    private final ContaPagarPagamentoRepository pagamentoRepository;
    private final ContaPagarEventoRepository eventoRepository;
    private final MovimentacaoBancoRepository movimentacaoRepository;
    private final BancoRepository bancoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final MovimentacaoBancoService movimentacaoService;
    private final ContaPagarMapper mapper;
    private final UsuarioAuditorAware auditor;
    private final PlatformTransactionManager transactionManager;

    private record Aplicada(ContaPagarPagamento pagamento, MovimentacaoBanco movimentacao) {
    }

    // ---------- pagamento individual ----------

    @Transactional
    public PagamentoDTOs.Resultado pagar(Long tituloId, PagamentoDTOs.Request r) {
        TextoUtils.validarTexto(r.observacao(), "A observação", 500);
        ContaPagar titulo = contaPagarService.buscarComLock(tituloId);
        Banco banco = bancoRepository.findByIdForUpdate(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        FormaPagamento forma = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        // confirmação do valor e do vencimento reais (título de valor variável) na mesma transação e sob o mesmo bloqueio; falha adiante = rollback de tudo
        PagamentoCalculo.Confirmacao confirmacao = PagamentoCalculo.confirmar(titulo, r.valorConta(), r.vencimento(),
                pagamentoRepository.existsByContaPagarIdAndEstornadaFalse(tituloId));
        if (confirmacao != null) {
            contaPagarRepository.save(titulo);
            registrar(titulo, "DADOS_CONFIRMADOS", confirmacao.descricao(), auditor.getCurrentAuditor().orElse(null));
        }
        Aplicada a = aplicar(titulo, banco, forma, r.dataPagamento(), r.valorPrincipal(), r.juros(), r.multa(), r.desconto(), r.observacao());
        return new PagamentoDTOs.Resultado(mapper.pagamento(a.pagamento(), a.movimentacao().getId()), contaPagarService.detalhe(titulo),
                movimentacaoService.avisoSaldoNegativo(List.of(banco)));
    }

    private Aplicada aplicar(ContaPagar titulo, Banco banco, FormaPagamento forma, LocalDate data, BigDecimal principal, BigDecimal juros,
                             BigDecimal multa, BigDecimal desconto, String observacao) {
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        PagamentoCalculo.ResultadoPagamento r = PagamentoCalculo.pagar(titulo, banco, forma, data, principal, juros, multa, desconto, observacao, usuario, LocalDate.now());
        ContaPagarPagamento pagamento = pagamentoRepository.save(r.pagamento());
        MovimentacaoBanco mov = r.movimentacao();
        mov.setPagamentoId(pagamento.getId());
        movimentacaoRepository.save(mov);
        contaPagarRepository.save(titulo);
        registrar(titulo, "PAGAMENTO_REGISTRADO", "Pagamento de " + MovimentacaoBancoService.moeda(pagamento.getValorTotal()) + " em " + TextoUtils.data(data)
                + " (principal " + MovimentacaoBancoService.moeda(pagamento.getValorPrincipal()) + ", juros " + MovimentacaoBancoService.moeda(pagamento.getJuros())
                + ", multa " + MovimentacaoBancoService.moeda(pagamento.getMulta()) + ", desconto " + MovimentacaoBancoService.moeda(pagamento.getDesconto())
                + "), banco \"" + banco.getNome() + "\", forma \"" + forma.getNome() + "\".", usuario);
        return new Aplicada(pagamento, mov);
    }

    // ---------- estorno ----------

    @Transactional
    public PagamentoDTOs.Resultado estornar(Long pagamentoId, EstornoRequestDTO request) {
        String motivo = CalculoFinanceiro.validarMotivo(request == null ? null : request.motivo());
        Long tituloId = pagamentoRepository.findContaPagarIdById(pagamentoId).orElseThrow(() -> new ObjetoNaoEncontradoException("Pagamento não encontrado."));
        ContaPagar titulo = contaPagarService.buscarComLock(tituloId);
        ContaPagarPagamento pagamento = pagamentoRepository.findByIdForUpdate(pagamentoId).orElseThrow(() -> new ObjetoNaoEncontradoException("Pagamento não encontrado."));
        if (pagamento.isEstornada()) throw new ItemInvalidoException("Este pagamento já foi estornado.");
        MovimentacaoBanco original = movimentacaoRepository.findByPagamentoIdAndOrigem(pagamentoId, OrigemMovimentacao.PAGAMENTO_CONTA_PAGAR).stream().findFirst()
                .orElseThrow(() -> new ItemInvalidoException("A movimentação bancária deste pagamento não foi localizada."));
        bancoRepository.findByIdForUpdate(original.getBanco().getId());

        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        MovimentacaoBanco inversa = PagamentoCalculo.estornar(pagamento, original, motivo, usuario, LocalDate.now(), LocalDateTime.now());
        movimentacaoRepository.save(inversa);
        pagamentoRepository.save(pagamento);
        contaPagarRepository.save(titulo);
        registrar(titulo, "PAGAMENTO_ESTORNADO", "Estorno do pagamento nº " + pagamento.getId() + " no valor total de " + MovimentacaoBancoService.moeda(pagamento.getValorTotal())
                + " (principal " + MovimentacaoBancoService.moeda(pagamento.getValorPrincipal()) + "), realizado em " + TextoUtils.data(pagamento.getDataPagamento())
                + ". Motivo: " + motivo, usuario);
        return new PagamentoDTOs.Resultado(mapper.pagamento(pagamento, original.getId()), contaPagarService.detalhe(titulo), null);
    }

    // ---------- pagamento em lote ----------

    // paga o saldo integral de cada título, cada um em transação própria; um título recusado nunca derruba o lote
    public PagamentoDTOs.ResultadoLote pagarLote(PagamentoDTOs.Lote r) {
        if (r == null || r.ids() == null || r.ids().isEmpty()) throw new ItemInvalidoException("Selecione ao menos um título.");
        if (r.ids().stream().anyMatch(Objects::isNull)) throw new ItemInvalidoException("A lista de títulos contém um identificador inválido.");
        if (r.ids().size() > LOTE_MAXIMO) throw new ItemInvalidoException("O pagamento em lote aceita no máximo " + LOTE_MAXIMO + " títulos.");
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
        List<PagamentoDTOs.ItemLote> itens = new ArrayList<>();
        BigDecimal totalPago = BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        int pagos = 0;
        for (Long id : new LinkedHashSet<>(r.ids())) {
            try {
                PagamentoDTOs.ItemLote item = tx.execute(status -> pagarIntegral(id, r));
                itens.add(item);
                pagos++;
                totalPago = totalPago.add(item.valorPago());
            } catch (ItemInvalidoException | ObjetoNaoEncontradoException e) {
                itens.add(new PagamentoDTOs.ItemLote(id, numeroOuNulo(id), false, null, null, null, null, e.getMessage()));
            }
        }
        String aviso = pagos == 0 ? null : movimentacaoService.avisoSaldoNegativo(List.of(bancoInicial));
        return new PagamentoDTOs.ResultadoLote(pagos, itens.size() - pagos, totalPago, itens, aviso);
    }

    private PagamentoDTOs.ItemLote pagarIntegral(Long id, PagamentoDTOs.Lote r) {
        ContaPagar titulo = contaPagarService.buscarComLock(id);
        Banco banco = bancoRepository.findByIdForUpdate(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        FormaPagamento forma = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        BigDecimal saldo = PagamentoCalculo.saldoIntegral(titulo);
        Aplicada a = aplicar(titulo, banco, forma, r.dataPagamento(), saldo, null, null, null, r.observacao());
        return new PagamentoDTOs.ItemLote(id, titulo.getNumero(), true, a.pagamento().getId(), a.pagamento().getValorTotal(),
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
