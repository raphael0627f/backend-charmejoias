package com.manager.app.financeiro;

import com.manager.app.acertoconsignacao.AcertoConsignacao;
import com.manager.app.acertoconsignacao.AcertoConsignacaoRepository;
import com.manager.app.cliente.Cliente;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.financeiro.DocumentoFinanceiroCalculo.Tipo;
import com.manager.app.financeiro.dto.ContaReceberDetalheDTO;
import com.manager.app.financeiro.dto.ContaReceberRequestDTO;
import com.manager.app.financeiro.dto.ContaReceberResumoDTO;
import com.manager.app.financeiro.dto.DocumentoFinanceiroDTOs;
import com.manager.app.preacerto.PreAcertoConsignacao;
import com.manager.app.preacerto.PreAcertoConsignacaoProdutoRepository;
import com.manager.app.preacerto.PreAcertoConsignacaoRepository;
import com.manager.app.preacerto.PreAcertoConsignacaoTotaisProjecao;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// resumo financeiro (valor, titulado, saldo) e geração de títulos a receber a partir do pré-acerto e do acerto de consignação.
// Concorrência: ordem fixa de bloqueios consignação -> documento -> títulos ativos da consignação -> banco (recebimento), de modo que duas gerações,
// uma geração e uma edição do pré-acerto ou uma geração e o cancelamento de um título nunca produzam divergência.
@Service
@RequiredArgsConstructor
public class DocumentoFinanceiroService {

    static final String CATEGORIA_GRUPO = "Vendas";
    static final String CATEGORIA_SUBGRUPO = "Consignação";

    private final PreAcertoConsignacaoRepository preAcertoRepository;
    private final PreAcertoConsignacaoProdutoRepository preAcertoProdutoRepository;
    private final AcertoConsignacaoRepository acertoRepository;
    private final ConsignacaoRepository consignacaoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final CategoriaReceitaRepository categoriaRepository;
    private final ContaReceberService contaReceberService;
    private final ContaReceberRecebimentoService recebimentoService;
    private final ContaReceberMapper mapper;

    // documento carregado com tudo o que o resumo precisa
    private record Documento(Tipo tipo, Long id, Consignacao consignacao, Cliente cliente, BigDecimal valor, boolean confirmado,
                             PreAcertoConsignacao preAcerto, AcertoConsignacao acerto) {
    }

    // ---------- consulta ----------

    @Transactional(readOnly = true)
    public DocumentoFinanceiroDTOs.Resumo resumoPreAcerto(Long id) {
        return resumo(carregarPreAcerto(id));
    }

    @Transactional(readOnly = true)
    public DocumentoFinanceiroDTOs.Resumo resumoAcerto(Long id) {
        return resumo(carregarAcerto(id));
    }

    private Documento carregarPreAcerto(Long id) {
        PreAcertoConsignacao p = preAcertoRepository.findById(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Pré-acerto de consignação não encontrado. Id: " + id));
        return documentoDe(p);
    }

    private Documento documentoDe(PreAcertoConsignacao p) {
        BigDecimal soma = preAcertoProdutoRepository.buscarTotais(List.of(p.getId())).stream().findFirst()
                .map(PreAcertoConsignacaoTotaisProjecao::getValorTotal).orElse(BigDecimal.ZERO);
        return new Documento(Tipo.PRE_ACERTO, p.getId(), p.getConsignacao(), p.getCliente(),
                DocumentoFinanceiroCalculo.valorPreAcerto(soma, p.getDesconto()), false, p, null);
    }

    private Documento carregarAcerto(Long id) {
        AcertoConsignacao a = acertoRepository.findById(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Acerto de consignação não encontrado. Id: " + id));
        return documentoDe(a);
    }

    private Documento documentoDe(AcertoConsignacao a) {
        return new Documento(Tipo.ACERTO, a.getId(), a.getConsignacao(), a.getConsignacao().getCliente(),
                CalculoFinanceiro.arredondar(a.getValorTotalComDesconto()), Boolean.TRUE.equals(a.getConfirmado()), null, a);
    }

    private DocumentoFinanceiroDTOs.Resumo resumo(Documento d) {
        return resumo(d, d.consignacao() == null ? List.of() : contaReceberRepository.findAtivosDaConsignacao(d.consignacao().getId()));
    }

    // ativosDaConsignacao: títulos não cancelados de toda a consignação (no pré-acerto só os próprios entram no saldo)
    private DocumentoFinanceiroDTOs.Resumo resumo(Documento d, List<ContaReceber> ativosDaConsignacao) {
        boolean preAcerto = d.tipo() == Tipo.PRE_ACERTO;
        List<ContaReceber> proprios = preAcerto ? contaReceberRepository.findByPreAcertoIdOrderByIdAsc(d.id())
                : contaReceberRepository.findByAcertoIdOrderByIdAsc(d.id());
        List<ContaReceber> consideradosNoSaldo = preAcerto
                ? ativosDaConsignacao.stream().filter(t -> t.getPreAcerto() != null && t.getPreAcerto().getId().equals(d.id())).toList()
                : ativosDaConsignacao;
        BigDecimal titulado = soma(consideradosNoSaldo, false);
        BigDecimal recebido = soma(consideradosNoSaldo, true);
        BigDecimal tituladoPreAcerto = preAcerto ? null
                : soma(ativosDaConsignacao.stream().filter(t -> t.getPreAcerto() != null).toList(), false);
        boolean tituloProprioAtivo = proprios.stream().anyMatch(t -> t.getSituacao() != SituacaoContaReceber.CANCELADO);
        boolean tituloDoAcertoAtivo = ativosDaConsignacao.stream().anyMatch(t -> t.getAcerto() != null);
        DocumentoFinanceiroCalculo.Saldo saldo = DocumentoFinanceiroCalculo.saldo(d.valor(), titulado);
        Cliente cliente = d.cliente();
        String motivo = DocumentoFinanceiroCalculo.motivoBloqueio(new DocumentoFinanceiroCalculo.Situacao(d.tipo(), d.consignacao() != null,
                d.confirmado(), cliente != null && cliente.isAtivo(), cliente == null ? null : cliente.getNome(), d.valor(), tituloProprioAtivo,
                tituloDoAcertoAtivo, saldo));
        boolean bloqueadoParaEdicao = preAcerto ? tituloProprioAtivo : (d.confirmado() || tituloProprioAtivo);
        List<DocumentoFinanceiroDTOs.Titulo> titulos = proprios.stream().map(t -> new DocumentoFinanceiroDTOs.Titulo(t.getId(), t.getNumero(),
                t.getParcelaNumero(), t.getParcelaTotal(), CalculoFinanceiro.arredondar(t.getValor()), t.getVencimento(), t.getSituacao(),
                CalculoFinanceiro.arredondar(t.getValorRecebido()))).toList();
        return new DocumentoFinanceiroDTOs.Resumo(d.tipo().name(), d.id(), d.consignacao() == null ? null : d.consignacao().getId(),
                cliente == null ? null : cliente.getCodigo(), cliente == null ? null : cliente.getNome(), d.valor(), titulado, recebido, saldo.saldoATitular(), saldo.excedente(),
                DocumentoFinanceiroCalculo.excedenteRecebido(d.valor(), recebido), tituladoPreAcerto, tituloProprioAtivo, bloqueadoParaEdicao,
                motivo == null, motivo, DocumentoFinanceiroCalculo.aviso(d.tipo(), saldo), titulos);
    }

    private static BigDecimal soma(List<ContaReceber> titulos, boolean recebido) {
        BigDecimal total = BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        for (ContaReceber t : titulos) total = total.add(CalculoFinanceiro.arredondar(recebido ? t.getValorRecebido() : t.getValor()));
        return total;
    }

    // ---------- geração ----------

    @Transactional
    public DocumentoFinanceiroDTOs.GerarResposta gerarDoPreAcerto(Long id, DocumentoFinanceiroDTOs.GerarRequest r) {
        List<Long> consignacaoIds = preAcertoRepository.consignacaoIdDe(id);
        if (consignacaoIds.isEmpty()) throw new ObjetoNaoEncontradoException("Pré-acerto de consignação não encontrado. Id: " + id);
        Long consignacaoId = consignacaoIds.get(0);
        if (consignacaoId == null) throw new ItemInvalidoException("O pré-acerto não possui consignação e não gera título a receber.");
        bloquearConsignacao(consignacaoId);
        PreAcertoConsignacao p = preAcertoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Pré-acerto de consignação não encontrado. Id: " + id));
        return gerar(documentoDe(p), r);
    }

    @Transactional
    public DocumentoFinanceiroDTOs.GerarResposta gerarDoAcerto(Long id, DocumentoFinanceiroDTOs.GerarRequest r) {
        List<Long> consignacaoIds = acertoRepository.consignacaoIdDe(id);
        if (consignacaoIds.isEmpty()) throw new ObjetoNaoEncontradoException("Acerto de consignação não encontrado. Id: " + id);
        bloquearConsignacao(consignacaoIds.get(0));
        AcertoConsignacao a = acertoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Acerto de consignação não encontrado. Id: " + id));
        return gerar(documentoDe(a), r);
    }

    private void bloquearConsignacao(Long consignacaoId) {
        consignacaoRepository.findByIdForUpdate(consignacaoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. Id: " + consignacaoId));
    }

    private DocumentoFinanceiroDTOs.GerarResposta gerar(Documento d, DocumentoFinanceiroDTOs.GerarRequest pedido) {
        DocumentoFinanceiroDTOs.GerarRequest r = pedido == null
                ? new DocumentoFinanceiroDTOs.GerarRequest(null, null, null, null, null, null, null, null, null, null) : pedido;
        int parcelas = r.quantidadeParcelas() == null ? 1 : r.quantidadeParcelas();
        if (parcelas < 1) throw new ItemInvalidoException("A quantidade de parcelas deve ser no mínimo 1.");
        if (parcelas > 1 && r.recebimento() != null) {
            throw new ItemInvalidoException("O recebimento no mesmo ato só é permitido para título simples. A baixa das parcelas é feita na tela do título.");
        }
        TextoUtils.validarTexto(r.descricao(), "A descrição", 200);

        // títulos ativos da consignação bloqueados antes do cálculo: serializa com o cancelamento de cada título
        List<ContaReceber> ativos = contaReceberRepository.findAtivosDaConsignacaoForUpdate(d.consignacao().getId());
        DocumentoFinanceiroDTOs.Resumo antes = resumo(d, ativos);
        if (!antes.podeGerar()) throw new ItemInvalidoException(antes.motivoBloqueio());

        CategoriaReceita categoria = categoriaRepository.findFirstByNomeIgnoreCaseAndPaiNomeIgnoreCase(CATEGORIA_SUBGRUPO, CATEGORIA_GRUPO)
                .orElseThrow(() -> new ItemInvalidoException("A categoria de receita \"" + CATEGORIA_GRUPO + " > " + CATEGORIA_SUBGRUPO + "\" não foi encontrada."));
        LocalDate hoje = LocalDate.now();
        // dataEmissao (opcional, aditivo): permite lançar o título com a data real do documento (vencimento não pode ser anterior à emissão)
        if (r.dataEmissao() != null && r.dataEmissao().isAfter(hoje)) throw new ItemInvalidoException("A data de emissão não pode ser futura.");
        LocalDate emissao = r.dataEmissao() == null ? hoje : r.dataEmissao();
        LocalDate vencimento = r.dataVencimento() == null ? emissao : r.dataVencimento();
        String descricao = r.descricao() == null || r.descricao().isBlank() ? DocumentoFinanceiroCalculo.descricaoAutomatica(d.tipo(), d.id())
                : r.descricao().trim();
        ContaReceberRequestDTO.Parcelamento parcelamento = parcelas == 1 ? null : new ContaReceberRequestDTO.Parcelamento(parcelas, vencimento,
                r.intervalo() == null ? IntervaloParcelamento.MENSAL : r.intervalo(), r.datas(), null);
        ContaReceberRequestDTO pedidoTitulo = new ContaReceberRequestDTO(descricao, d.cliente().getId(), null, null, categoria.getId(), r.centroCustoId(),
                antes.saldoATitular(), emissao, null, vencimento, null, r.formaPagamentoId(), r.observacao(), parcelamento);

        ContaReceberService.Criados criados = contaReceberService.criarTitulos(pedidoTitulo,
                new ContaReceberService.Origem(d.consignacao(), d.preAcerto(), d.acerto()));

        ContaReceberDetalheDTO.Recebimento recebimento = null;
        if (r.recebimento() != null) {
            recebimento = recebimentoService.receberNoAto(criados.titulos().get(0), r.recebimento());
        }

        List<ContaReceberResumoDTO> resumos = new ArrayList<>();
        for (ContaReceber t : criados.titulos()) resumos.add(mapper.resumo(t, hoje));
        List<ContaReceber> ativosDepois = contaReceberRepository.findAtivosDaConsignacao(d.consignacao().getId());
        return new DocumentoFinanceiroDTOs.GerarResposta(criados.grupo(), resumos, recebimento, resumo(d, ativosDepois));
    }

}
