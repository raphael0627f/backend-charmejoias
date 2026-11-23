package com.manager.app.financeiro;

import com.manager.app.acertoconsignacao.AcertoConsignacao;
import com.manager.app.acertoconsignacao.AcertoConsignacaoRepository;
import com.manager.app.cliente.ClienteRepository;
import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.financeiro.ClienteFinanceiroCalculo.Filtro;
import com.manager.app.financeiro.ClienteFinanceiroCalculo.Selo;
import com.manager.app.financeiro.dto.ClienteFinanceiroDTOs;
import com.manager.app.financeiro.dto.DocumentoFinanceiroDTOs;
import com.manager.app.preacerto.PreAcertoConsignacao;
import com.manager.app.preacerto.PreAcertoConsignacaoRepository;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// aba Financeiro do cliente. Documento da consignação: o ACERTO, se existir (ele já contém todos os produtos acertados); senão o PRÉ-ACERTO
// (a consignação tem no máximo um de cada); nunca se somam os dois, portanto vendido e comissão não são contados em dobro.
// Títulos cancelados ficam fora de todas as somas. "Valor pago" = total real dos recebimentos não estornados (crédito do cliente conta).
@Service
@RequiredArgsConstructor
public class ClienteFinanceiroService {

    private final ClienteRepository clienteRepository;
    private final ConsignacaoRepository consignacaoRepository;
    private final PreAcertoConsignacaoRepository preAcertoRepository;
    private final AcertoConsignacaoRepository acertoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final ContaReceberRecebimentoRepository recebimentoRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final DocumentoValoresService documentoValores;

    private record Doc(String tipo, Long id, DocumentoValoresService.Valores valores) {
    }

    // acumulador por consignação (ou pelo grupo sem consignação)
    private static final class Acum {
        int titulos;
        int cancelados;
        BigDecimal pago = BigDecimal.ZERO;
        BigDecimal credito = BigDecimal.ZERO;
        BigDecimal taxa = BigDecimal.ZERO;
        BigDecimal titulado = BigDecimal.ZERO;
        BigDecimal aberto = BigDecimal.ZERO;
        LocalDate vencidoMaisAntigo;

        int ativos() {
            return titulos - cancelados;
        }

        void somar(ContaReceber t, TotaisRecebimento pg, LocalDate hoje) {
            titulos++;
            if (t.getSituacao() == SituacaoContaReceber.CANCELADO) {
                cancelados++;
                return;
            }
            pago = pago.add(pg.total());
            titulado = titulado.add(CalculoFinanceiro.arredondar(t.getValor()));
            credito = credito.add(pg.credito());
            taxa = taxa.add(pg.taxaCliente());
            BigDecimal aberto = ContaReceberCalculo.valorAberto(t.getSituacao(), t.getValor(), t.getValorRecebido());
            this.aberto = this.aberto.add(aberto);
            boolean emAberto = t.getSituacao() == SituacaoContaReceber.ABERTO || t.getSituacao() == SituacaoContaReceber.RECEBIDO_PARCIAL;
            if (emAberto && t.getVencimento() != null && t.getVencimento().isBefore(hoje)
                    && (vencidoMaisAntigo == null || t.getVencimento().isBefore(vencidoMaisAntigo))) {
                vencidoMaisAntigo = t.getVencimento();
            }
        }
    }

    @Transactional(readOnly = true)
    public ClienteFinanceiroDTOs.ConsignacoesPagina consignacoes(UUID clienteId, String filtroTexto, int page, int size) {
        garantirCliente(clienteId);
        Filtro filtro = ClienteFinanceiroCalculo.filtro(filtroTexto);
        int pagina = ClienteFinanceiroCalculo.pagina(page);
        int tamanho = ClienteFinanceiroCalculo.tamanho(size);
        LocalDate hoje = LocalDate.now();

        List<Consignacao> consignacoes = consignacaoRepository.findByCliente_Id(clienteId);
        List<ContaReceber> titulos = contaReceberRepository.findDoCliente(clienteId);
        Map<Long, TotaisRecebimento> pagos = new HashMap<>();
        if (!titulos.isEmpty()) recebimentoRepository.totaisPorTitulo(titulos.stream().map(ContaReceber::getId).toList()).forEach(t -> pagos.put(t.tituloId(), t));

        Map<Long, Acum> porConsignacao = new HashMap<>();
        Acum semConsignacao = new Acum();
        for (ContaReceber t : titulos) {
            Acum a = t.getConsignacao() == null ? semConsignacao : porConsignacao.computeIfAbsent(t.getConsignacao().getId(), k -> new Acum());
            a.somar(t, TotaisRecebimento.de(pagos, t.getId()), hoje);
        }

        List<Long> ids = consignacoes.stream().map(Consignacao::getId).toList();
        Map<Long, Doc> documentos = documentos(ids);

        List<ClienteFinanceiroDTOs.Consignacao> todas = new ArrayList<>();
        for (Consignacao c : consignacoes) {
            Doc doc = documentos.get(c.getId());
            Acum a = porConsignacao.getOrDefault(c.getId(), new Acum());
            // consignação sem documento e sem título não tem valor financeiro: não aparece nesta aba
            if (doc == null && a.titulos == 0) continue;
            todas.add(linha(c, doc, a));
        }
        todas.sort(Comparator.comparing(ClienteFinanceiroDTOs.Consignacao::data, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ClienteFinanceiroDTOs.Consignacao::consignacaoId, Comparator.reverseOrder()));
        List<ClienteFinanceiroDTOs.Consignacao> filtradas = todas.stream().filter(l -> filtro.aceita(Selo.valueOf(l.selo()))).collect(Collectors.toList());

        int total = filtradas.size();
        int totalPaginas = (int) Math.ceil(total / (double) tamanho);
        int de = Math.min(pagina * tamanho, total);
        List<ClienteFinanceiroDTOs.Consignacao> conteudo = filtradas.subList(de, Math.min(de + tamanho, total));

        ClienteFinanceiroDTOs.SemConsignacao sem = null;
        if (pagina == 0 && semConsignacao.titulos > 0) {
            Selo selo = ClienteFinanceiroCalculo.selo(false, semConsignacao.titulos, semConsignacao.ativos(), false, null, semConsignacao.aberto,
                    semConsignacao.pago, semConsignacao.vencidoMaisAntigo != null);
            sem = new ClienteFinanceiroDTOs.SemConsignacao(semConsignacao.titulos, semConsignacao.cancelados, CalculoFinanceiro.arredondar(semConsignacao.pago),
                    CalculoFinanceiro.arredondar(semConsignacao.credito), CalculoFinanceiro.arredondar(semConsignacao.aberto), semConsignacao.vencidoMaisAntigo, selo.name());
        }
        return new ClienteFinanceiroDTOs.ConsignacoesPagina(conteudo, pagina, tamanho, total, totalPaginas, filtro.name(), resumo(filtradas), sem);
    }

    private ClienteFinanceiroDTOs.Consignacao linha(Consignacao c, Doc doc, Acum a) {
        DocumentoValoresService.Valores v = doc == null ? DocumentoValoresService.Valores.de(BigDecimal.ZERO, BigDecimal.ZERO) : doc.valores();
        boolean cancelada = Boolean.TRUE.equals(c.getCancelada());
        Selo selo = ClienteFinanceiroCalculo.selo(cancelada, a.titulos, a.ativos(), doc != null, v.valorAPagar(), a.aberto, a.pago, a.vencidoMaisAntigo != null);
        LocalDate data = c.getDataEntrega() != null ? c.getDataEntrega() : (c.getCreatedAt() == null ? null : c.getCreatedAt().toLocalDate());
        return new ClienteFinanceiroDTOs.Consignacao(c.getId(), c.getId(), data, doc == null ? null : doc.tipo(), doc == null ? null : doc.id(), v.valorVendido(),
                v.valorComissao(), v.valorAPagar(), CalculoFinanceiro.arredondar(a.pago), CalculoFinanceiro.arredondar(a.credito), a.credito.signum() > 0,
                CalculoFinanceiro.arredondar(a.taxa), CalculoFinanceiro.arredondar(a.aberto), a.vencidoMaisAntigo, selo.name(), a.titulos, a.cancelados,
                CalculoFinanceiro.arredondar(a.titulado));
    }

    // consignação cancelada não entra nas somas de vendido/comissão/a pagar
    private static ClienteFinanceiroDTOs.Resumo resumo(List<ClienteFinanceiroDTOs.Consignacao> linhas) {
        BigDecimal vendido = BigDecimal.ZERO;
        BigDecimal comissao = BigDecimal.ZERO;
        BigDecimal aPagar = BigDecimal.ZERO;
        BigDecimal pago = BigDecimal.ZERO;
        BigDecimal credito = BigDecimal.ZERO;
        BigDecimal aberto = BigDecimal.ZERO;
        for (ClienteFinanceiroDTOs.Consignacao l : linhas) {
            if (!Selo.CANCELADA.name().equals(l.selo())) {
                vendido = vendido.add(l.valorVendido());
                comissao = comissao.add(l.valorComissao());
                aPagar = aPagar.add(l.valorAPagar());
            }
            pago = pago.add(l.valorPago());
            credito = credito.add(l.valorCreditoAplicado());
            aberto = aberto.add(l.emAberto());
        }
        return new ClienteFinanceiroDTOs.Resumo(linhas.size(), CalculoFinanceiro.arredondar(vendido), CalculoFinanceiro.arredondar(comissao),
                CalculoFinanceiro.arredondar(aPagar), CalculoFinanceiro.arredondar(pago), CalculoFinanceiro.arredondar(credito), CalculoFinanceiro.arredondar(aberto));
    }

    private Map<Long, Doc> documentos(List<Long> consignacaoIds) {
        Map<Long, Doc> mapa = new HashMap<>();
        if (consignacaoIds.isEmpty()) return mapa;
        for (PreAcertoConsignacao p : preAcertoRepository.findDasConsignacoes(consignacaoIds)) {
            mapa.put(p.getConsignacao().getId(), new Doc("PRE_ACERTO", p.getId(), documentoValores.doPreAcerto(p)));
        }
        // o acerto prevalece sobre o pré-acerto
        for (AcertoConsignacao a : acertoRepository.findDasConsignacoes(consignacaoIds)) {
            mapa.put(a.getConsignacao().getId(), new Doc("ACERTO", a.getId(), documentoValores.doAcerto(a)));
        }
        return mapa;
    }

    @Transactional(readOnly = true)
    public ClienteFinanceiroDTOs.TitulosPagina titulosDaConsignacao(UUID clienteId, Long consignacaoId, int page, int size) {
        garantirCliente(clienteId);
        Consignacao c = consignacaoRepository.findById(consignacaoId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Consignação não encontrada. Id: " + consignacaoId));
        if (c.getCliente() == null || !c.getCliente().getId().equals(clienteId)) {
            throw new ObjetoNaoEncontradoException("Consignação não encontrada para o cliente informado. Id: " + consignacaoId);
        }
        return paginaDeTitulos(contaReceberRepository.findDaConsignacaoDoCliente(clienteId, consignacaoId, pageable(page, size)));
    }

    @Transactional(readOnly = true)
    public ClienteFinanceiroDTOs.TitulosPagina titulosSemConsignacao(UUID clienteId, int page, int size) {
        garantirCliente(clienteId);
        return paginaDeTitulos(contaReceberRepository.findSemConsignacaoDoCliente(clienteId, pageable(page, size)));
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(ClienteFinanceiroCalculo.pagina(page), ClienteFinanceiroCalculo.tamanho(size));
    }

    private ClienteFinanceiroDTOs.TitulosPagina paginaDeTitulos(Page<ContaReceber> pagina) {
        Map<Long, TotaisRecebimento> pagos = new HashMap<>();
        List<Long> ids = pagina.getContent().stream().map(ContaReceber::getId).toList();
        if (!ids.isEmpty()) recebimentoRepository.totaisPorTitulo(ids).forEach(t -> pagos.put(t.tituloId(), t));
        List<ClienteFinanceiroDTOs.TituloLinha> linhas = pagina.getContent().stream().map(t -> {
            TotaisRecebimento pg = TotaisRecebimento.de(pagos, t.getId());
            boolean cancelado = t.getSituacao() == SituacaoContaReceber.CANCELADO;
            DocumentoFinanceiroDTOs.Origem origem = t.getAcerto() != null ? new DocumentoFinanceiroDTOs.Origem("ACERTO", t.getAcerto().getId(), t.getAcerto().getId())
                    : t.getPreAcerto() != null ? new DocumentoFinanceiroDTOs.Origem("PRE_ACERTO", t.getPreAcerto().getId(), t.getPreAcerto().getId())
                    : new DocumentoFinanceiroDTOs.Origem("OUTRO", null, null);
            return new ClienteFinanceiroDTOs.TituloLinha(t.getId(), t.getNumero(), t.getDescricao(), t.getParcelaNumero(), t.getParcelaTotal(), t.getVencimento(),
                    CalculoFinanceiro.arredondar(t.getValor()), CalculoFinanceiro.arredondar(cancelado ? BigDecimal.ZERO : pg.total()),
                    CalculoFinanceiro.arredondar(cancelado ? BigDecimal.ZERO : pg.credito()),
                    ContaReceberCalculo.valorAberto(t.getSituacao(), t.getValor(), t.getValorRecebido()), t.getSituacao(), cancelado, origem);
        }).toList();
        return new ClienteFinanceiroDTOs.TitulosPagina(linhas, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ClienteFinanceiroDTOs.PagarPagina contasAPagar(UUID clienteId, int page, int size) {
        garantirCliente(clienteId);
        Page<ContaPagar> pagina = contaPagarRepository.findDoCliente(clienteId, pageable(page, size));
        List<ClienteFinanceiroDTOs.PagarLinha> linhas = pagina.getContent().stream().map(c -> {
            boolean cancelado = c.getSituacao() == SituacaoContaPagar.CANCELADO;
            BigDecimal aberto = cancelado ? BigDecimal.ZERO : c.getValor().subtract(c.getValorPago() == null ? BigDecimal.ZERO : c.getValorPago()).max(BigDecimal.ZERO);
            return new ClienteFinanceiroDTOs.PagarLinha(c.getId(), c.getNumero(), c.getDescricao(), c.getEmissao(), c.getVencimento(),
                    CalculoFinanceiro.arredondar(c.getValor()), CalculoFinanceiro.arredondar(c.getValorPago()), CalculoFinanceiro.arredondar(aberto),
                    c.getSituacao().name(), cancelado);
        }).toList();
        Object[] t = contaPagarRepository.totaisDoCliente(clienteId).get(0);
        ClienteFinanceiroDTOs.PagarTotais totais = new ClienteFinanceiroDTOs.PagarTotais(((Number) t[0]).longValue(), valor(t[1]), valor(t[2]), valor(t[3]));
        return new ClienteFinanceiroDTOs.PagarPagina(linhas, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages(), totais);
    }

    private static BigDecimal valor(Object o) {
        return o == null ? CalculoFinanceiro.arredondar(BigDecimal.ZERO) : CalculoFinanceiro.arredondar((BigDecimal) o);
    }

    private void garantirCliente(UUID clienteId) {
        if (!clienteRepository.existsById(clienteId)) throw new ObjetoNaoEncontradoException("Cliente não encontrado. Id: " + clienteId);
    }
}
