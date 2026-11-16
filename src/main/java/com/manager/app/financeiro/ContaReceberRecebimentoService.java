package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.EstornoRequestDTO;
import com.manager.app.financeiro.dto.RecebimentoDTOs;
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

// recebimento, estorno de recebimento e recebimento em lote. Concorrência: bloqueio pessimista do título e depois do banco (sempre nessa ordem),
// para que dois recebimentos simultâneos nunca recebam o mesmo saldo duas vezes. Regras e cálculos em RecebimentoCalculo.
@Service
@RequiredArgsConstructor
public class ContaReceberRecebimentoService {

    static final int LOTE_MAXIMO = 200;

    private final ContaReceberService contaReceberService;
    private final ContaReceberRepository contaReceberRepository;
    private final ContaReceberRecebimentoRepository recebimentoRepository;
    private final ContaReceberEventoRepository eventoRepository;
    private final MovimentacaoBancoRepository movimentacaoRepository;
    private final BancoRepository bancoRepository;
    private final FormaPagamentoRepository formaPagamentoRepository;
    private final MovimentacaoBancoService movimentacaoService;
    private final ContaReceberMapper mapper;
    private final UsuarioAuditorAware auditor;
    private final PlatformTransactionManager transactionManager;

    private record Aplicado(ContaReceberRecebimento recebimento, MovimentacaoBanco movimentacao) {
    }

    // ---------- recebimento individual ----------

    @Transactional
    public RecebimentoDTOs.Resultado receber(Long tituloId, RecebimentoDTOs.Request r) {
        TextoUtils.validarTexto(r.observacao(), "A observação", 500);
        ContaReceber titulo = contaReceberService.buscarComLock(tituloId);
        Banco banco = bancoRepository.findByIdForUpdate(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        FormaPagamento forma = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        Aplicado a = aplicar(titulo, banco, forma, r.dataRecebimento(), r.valorPrincipal(), r.juros(), r.multa(), r.desconto(), r.observacao());
        return new RecebimentoDTOs.Resultado(mapper.recebimento(a.recebimento(), a.movimentacao().getId()), contaReceberService.detalhe(titulo), null);
    }

    // recebimento no mesmo ato da geração do título (chamado dentro da transação de quem gerou): mesmas regras e mesmo bloqueio do recebimento normal
    @Transactional
    public com.manager.app.financeiro.dto.ContaReceberDetalheDTO.Recebimento receberNoAto(ContaReceber titulo, RecebimentoDTOs.Request r) {
        if (r.dataRecebimento() == null) throw new ItemInvalidoException("A data do recebimento é obrigatória.");
        if (r.valorPrincipal() == null) throw new ItemInvalidoException("O valor principal é obrigatório.");
        if (r.bancoId() == null) throw new ItemInvalidoException("O banco é obrigatório.");
        if (r.formaPagamentoId() == null) throw new ItemInvalidoException("A forma de pagamento é obrigatória.");
        TextoUtils.validarTexto(r.observacao(), "A observação", 500);
        Banco banco = bancoRepository.findByIdForUpdate(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        FormaPagamento forma = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        Aplicado a = aplicar(titulo, banco, forma, r.dataRecebimento(), r.valorPrincipal(), r.juros(), r.multa(), r.desconto(), r.observacao());
        return mapper.recebimento(a.recebimento(), a.movimentacao().getId());
    }

    private Aplicado aplicar(ContaReceber titulo, Banco banco, FormaPagamento forma, LocalDate data, BigDecimal principal, BigDecimal juros,
                             BigDecimal multa, BigDecimal desconto, String observacao) {
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        RecebimentoCalculo.ResultadoRecebimento r = RecebimentoCalculo.receber(titulo, banco, forma, data, principal, juros, multa, desconto, observacao,
                usuario, LocalDate.now());
        ContaReceberRecebimento recebimento = recebimentoRepository.save(r.recebimento());
        MovimentacaoBanco mov = r.movimentacao();
        mov.setRecebimentoId(recebimento.getId());
        movimentacaoRepository.save(mov);
        contaReceberRepository.save(titulo);
        registrar(titulo, "RECEBIMENTO_REGISTRADO", "Recebimento de " + MovimentacaoBancoService.moeda(recebimento.getValorTotal()) + " em " + TextoUtils.data(data)
                + " (principal " + MovimentacaoBancoService.moeda(recebimento.getValorPrincipal()) + ", juros " + MovimentacaoBancoService.moeda(recebimento.getJuros())
                + ", multa " + MovimentacaoBancoService.moeda(recebimento.getMulta()) + ", desconto " + MovimentacaoBancoService.moeda(recebimento.getDesconto())
                + "), banco \"" + banco.getNome() + "\", forma \"" + forma.getNome() + "\".", usuario);
        return new Aplicado(recebimento, mov);
    }

    // ---------- estorno ----------

    @Transactional
    public RecebimentoDTOs.Resultado estornar(Long recebimentoId, EstornoRequestDTO request) {
        String motivo = CalculoFinanceiro.validarMotivo(request == null ? null : request.motivo());
        Long tituloId = recebimentoRepository.findContaReceberIdById(recebimentoId).orElseThrow(() -> new ObjetoNaoEncontradoException("Recebimento não encontrado."));
        ContaReceber titulo = contaReceberService.buscarComLock(tituloId);
        ContaReceberRecebimento recebimento = recebimentoRepository.findByIdForUpdate(recebimentoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Recebimento não encontrado."));
        if (recebimento.isEstornada()) throw new ItemInvalidoException("Este recebimento já foi estornado.");
        MovimentacaoBanco original = movimentacaoRepository.findByRecebimentoIdAndOrigem(recebimentoId, OrigemMovimentacao.RECEBIMENTO_CONTA_RECEBER).stream().findFirst()
                .orElseThrow(() -> new ItemInvalidoException("A movimentação bancária deste recebimento não foi localizada."));
        Banco banco = bancoRepository.findByIdForUpdate(original.getBanco().getId()).orElseThrow();

        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        MovimentacaoBanco inversa = RecebimentoCalculo.estornar(recebimento, original, motivo, usuario, LocalDate.now(), LocalDateTime.now());
        movimentacaoRepository.save(inversa);
        recebimentoRepository.save(recebimento);
        contaReceberRepository.save(titulo);
        registrar(titulo, "RECEBIMENTO_ESTORNADO", "Estorno do recebimento nº " + recebimento.getId() + " no valor total de " + MovimentacaoBancoService.moeda(recebimento.getValorTotal())
                + " (principal " + MovimentacaoBancoService.moeda(recebimento.getValorPrincipal()) + "), realizado em " + TextoUtils.data(recebimento.getDataRecebimento())
                + ". Motivo: " + motivo, usuario);
        return new RecebimentoDTOs.Resultado(mapper.recebimento(recebimento, original.getId()), contaReceberService.detalhe(titulo),
                movimentacaoService.avisoSaldoNegativo(List.of(banco)));
    }

    // ---------- recebimento em lote ----------

    // recebe o saldo integral de cada título, cada um em transação própria; um título recusado nunca derruba o lote
    public RecebimentoDTOs.ResultadoLote receberLote(RecebimentoDTOs.Lote r) {
        if (r == null || r.ids() == null || r.ids().isEmpty()) throw new ItemInvalidoException("Selecione ao menos um título.");
        if (r.ids().stream().anyMatch(Objects::isNull)) throw new ItemInvalidoException("A lista de títulos contém um identificador inválido.");
        if (r.ids().size() > LOTE_MAXIMO) throw new ItemInvalidoException("O recebimento em lote aceita no máximo " + LOTE_MAXIMO + " títulos.");
        if (r.dataRecebimento() == null) throw new ItemInvalidoException("A data do recebimento é obrigatória.");
        if (r.bancoId() == null) throw new ItemInvalidoException("O banco é obrigatório.");
        if (r.formaPagamentoId() == null) throw new ItemInvalidoException("A forma de pagamento é obrigatória.");
        TextoUtils.validarTexto(r.observacao(), "A observação", 500);
        CalculoFinanceiro.validarDataNaoFutura(r.dataRecebimento(), LocalDate.now());
        Banco bancoInicial = bancoRepository.findById(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        CalculoFinanceiro.validarBancoAtivo(bancoInicial);
        FormaPagamento formaInicial = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        if (!formaInicial.isAtivo()) throw new ItemInvalidoException("A forma de pagamento \"" + formaInicial.getNome() + "\" está desativada.");

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<RecebimentoDTOs.ItemLote> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        int recebidos = 0;
        for (Long id : new LinkedHashSet<>(r.ids())) {
            try {
                RecebimentoDTOs.ItemLote item = tx.execute(status -> receberIntegral(id, r));
                itens.add(item);
                recebidos++;
                total = total.add(item.valorRecebido());
            } catch (ItemInvalidoException | ObjetoNaoEncontradoException e) {
                itens.add(new RecebimentoDTOs.ItemLote(id, numeroOuNulo(id), false, null, null, null, null, e.getMessage()));
            }
        }
        return new RecebimentoDTOs.ResultadoLote(recebidos, itens.size() - recebidos, total, itens);
    }

    private RecebimentoDTOs.ItemLote receberIntegral(Long id, RecebimentoDTOs.Lote r) {
        ContaReceber titulo = contaReceberService.buscarComLock(id);
        Banco banco = bancoRepository.findByIdForUpdate(r.bancoId()).orElseThrow(() -> new ItemInvalidoException("O banco informado não foi encontrado."));
        FormaPagamento forma = formaPagamentoRepository.findById(r.formaPagamentoId())
                .orElseThrow(() -> new ItemInvalidoException("A forma de pagamento informada não foi encontrada."));
        BigDecimal saldo = RecebimentoCalculo.saldoIntegral(titulo);
        Aplicado a = aplicar(titulo, banco, forma, r.dataRecebimento(), saldo, null, null, null, r.observacao());
        return new RecebimentoDTOs.ItemLote(id, titulo.getNumero(), true, a.recebimento().getId(), a.recebimento().getValorTotal(),
                ContaReceberCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorRecebido()), titulo.getSituacao().name(), null);
    }

    private Long numeroOuNulo(Long id) {
        return contaReceberRepository.findById(id).map(ContaReceber::getNumero).orElse(null);
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
