package com.manager.app.indicadorconsignacao;

import com.manager.app.indicadorconsignacao.IndicadorConsignacaoRepository.Filtro;
import com.manager.app.indicadorconsignacao.dto.IndicadorDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IndicadorConsignacaoService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Set<String> ORDENACOES = Set.of("vendido", "inadimplencia", "prazomedio", "cliente");

    private final IndicadorConsignacaoRepository repository;

    // período padrão: os últimos 12 meses (do primeiro dia do mês de há 11 meses até hoje)
    Filtro filtro(LocalDate inicio, LocalDate fim, UUID clienteId, Long cidadeId, Long rotaId, LocalDate hoje) {
        LocalDate fimPeriodo = fim == null ? hoje : fim;
        LocalDate inicioPeriodo = inicio == null ? hoje.minusMonths(11).withDayOfMonth(1) : inicio;
        if (inicioPeriodo.isAfter(fimPeriodo)) throw new ItemInvalidoException("A data inicial não pode ser posterior à data final.");
        if (inicioPeriodo.plusYears(10).isBefore(fimPeriodo)) throw new ItemInvalidoException("O período selecionado excede o limite de 10 anos. Reduza o intervalo entre as datas e tente novamente.");
        if (clienteId != null && !repository.clienteExiste(clienteId)) throw new ObjetoNaoEncontradoException("Cliente não encontrado.");
        List<String> ibges = null;
        if (cidadeId != null) {
            ibges = repository.ibgesDaCidade(cidadeId);
            if (ibges == null) throw new ObjetoNaoEncontradoException("Cidade não encontrada.");
        }
        if (rotaId != null) {
            List<String> daRota = repository.ibgesDaRota(rotaId);
            if (daRota == null) throw new ObjetoNaoEncontradoException("Rota não encontrada.");
            if (ibges == null) {
                ibges = daRota;
            } else {
                List<String> inter = new ArrayList<>(ibges);
                inter.retainAll(daRota);
                ibges = inter;
            }
        }
        return new Filtro(inicioPeriodo, fimPeriodo, hoje, clienteId, ibges);
    }

    public Filtro filtro(LocalDate inicio, LocalDate fim, UUID clienteId, Long cidadeId, Long rotaId) {
        return filtro(inicio, fim, clienteId, cidadeId, rotaId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public IndicadorDTOs.Resumo resumo(Filtro f) {
        var vendas = repository.vendas(f);
        Map<String, Long> quantidades = new HashMap<>();
        Map<String, BigDecimal> valores = repository.inadimplenciaValorPorFaixa(f, quantidades);
        var prazo = repository.prazo(f);
        var giro = repository.giro(f);
        return new IndicadorDTOs.Resumo(new IndicadorDTOs.Periodo(f.inicio(), f.fim()), IndicadorCalculo.vendas(vendas.acertos(), vendas.vendido(), vendas.comissao()),
                IndicadorCalculo.inadimplencia(valores, quantidades),
                IndicadorCalculo.prazo(prazo.pagos(), prazo.somaDias(), prazo.pagosComAtraso(), prazo.somaDiasAtraso(), prazo.pagosNoPrazo()),
                IndicadorCalculo.giro(giro.acertos(), giro.vendido(), giro.devolvido(), giro.qtdVendida(), giro.qtdDevolvida()));
    }

    // ---------- ranking de clientes ----------

    @Transactional(readOnly = true)
    public IndicadorDTOs.Ranking ranking(Filtro f, String ordenarTexto, String direcaoTexto, int top) {
        String ordenar = ordenarTexto == null || ordenarTexto.isBlank() ? "vendido" : ordenarTexto.trim().toLowerCase(Locale.ROOT);
        if (!ORDENACOES.contains(ordenar)) throw new ItemInvalidoException("Campo de ordenação inválido.");
        boolean desc = !(ordenar.equals("cliente"));
        if (direcaoTexto != null && !direcaoTexto.isBlank()) {
            if (!direcaoTexto.trim().equalsIgnoreCase("asc") && !direcaoTexto.trim().equalsIgnoreCase("desc")) throw new ItemInvalidoException("Direção inválida. Use asc ou desc.");
            desc = direcaoTexto.trim().equalsIgnoreCase("desc");
        }
        if (top < 1 || top > 100) throw new ItemInvalidoException("O limite deve estar entre 1 e 100.");

        Map<UUID, IndicadorConsignacaoRepository.ClienteVendas> vendas = new LinkedHashMap<>();
        repository.vendasPorCliente(f).forEach(v -> vendas.put(v.clienteId(), v));
        Map<UUID, IndicadorConsignacaoRepository.ClienteInad> inad = new LinkedHashMap<>();
        repository.inadimplenciaPorCliente(f).forEach(v -> inad.put(v.clienteId(), v));
        Map<UUID, IndicadorConsignacaoRepository.PrazoLinha> prazos = new LinkedHashMap<>();
        repository.prazoPorCliente(f).forEach(v -> prazos.put(v.clienteId(), v.prazo()));
        Set<UUID> ids = new LinkedHashSet<>();
        ids.addAll(vendas.keySet());
        ids.addAll(inad.keySet());
        ids.addAll(prazos.keySet());
        Map<UUID, IndicadorConsignacaoRepository.ClienteNome> nomes = new HashMap<>();
        repository.clientes(ids).forEach(n -> nomes.put(n.id(), n));

        List<IndicadorDTOs.ClienteRanking> itens = new ArrayList<>();
        for (UUID id : ids) {
            var v = vendas.get(id);
            var i = inad.get(id);
            var p = prazos.get(id);
            var n = nomes.get(id);
            BigDecimal prazoMedio = p == null || p.pagos() == 0 ? null : BigDecimal.valueOf(p.somaDias()).divide(BigDecimal.valueOf(p.pagos()), 1, java.math.RoundingMode.HALF_UP);
            itens.add(new IndicadorDTOs.ClienteRanking(id, n == null ? "" : n.nome(), n == null ? null : n.cidade(), v == null ? ZERO : v.vendido().setScale(2), v == null ? 0 : v.acertos(),
                    i == null ? ZERO : i.valor().setScale(2), i == null ? 0 : i.titulos(), prazoMedio, p == null ? 0 : p.pagos()));
        }
        Comparator<IndicadorDTOs.ClienteRanking> cmp = switch (ordenar) {
            case "inadimplencia" -> Comparator.comparing(IndicadorDTOs.ClienteRanking::inadimplenciaValor);
            // sem dado (nulo) fica sempre por último, nas duas direções
            case "prazomedio" -> Comparator.comparing(IndicadorDTOs.ClienteRanking::prazoMedioDias,
                    Comparator.nullsLast(desc ? Comparator.<BigDecimal>reverseOrder() : Comparator.<BigDecimal>naturalOrder()));
            case "cliente" -> Comparator.comparing(c -> c.cliente().toLowerCase(Locale.ROOT));
            default -> Comparator.comparing(IndicadorDTOs.ClienteRanking::valorVendido);
        };
        if (desc && !ordenar.equals("prazomedio")) cmp = cmp.reversed();
        // empate pelo nome para a ordem ser estável
        itens.sort(cmp.thenComparing(c -> c.cliente().toLowerCase(Locale.ROOT)));
        return new IndicadorDTOs.Ranking(itens.size() > top ? new ArrayList<>(itens.subList(0, top)) : itens, itens.size());
    }

    // ---------- evolução mensal ----------

    @Transactional(readOnly = true)
    public IndicadorDTOs.Evolucao evolucaoMensal(Filtro f) {
        Map<String, IndicadorConsignacaoRepository.MesVendas> vendas = new HashMap<>();
        repository.vendasPorMes(f).forEach(v -> vendas.put(v.mes(), v));
        Map<String, IndicadorConsignacaoRepository.MesInad> inad = new HashMap<>();
        repository.inadimplenciaPorMes(f).forEach(v -> inad.put(v.mes(), v));
        List<IndicadorDTOs.Mes> meses = new ArrayList<>();
        long acertos = 0;
        for (String m : IndicadorCalculo.meses(f.inicio(), f.fim())) {
            var v = vendas.get(m);
            var i = inad.get(m);
            BigDecimal vendido = v == null ? ZERO : v.vendido().setScale(2), comissao = v == null ? ZERO : v.comissao().setScale(2);
            if (v != null) acertos += v.acertos();
            meses.add(new IndicadorDTOs.Mes(m, vendido, comissao, IndicadorCalculo.percentual(comissao, vendido), i == null ? ZERO : i.valor().setScale(2), i == null ? 0 : i.titulos(),
                    v == null ? 0 : v.acertos()));
        }
        return new IndicadorDTOs.Evolucao(meses, IndicadorDTOs.Base.acertos(acertos));
    }

    // ---------- concentração ----------

    @Transactional(readOnly = true)
    public IndicadorDTOs.Concentracao concentracao(Filtro f) {
        List<IndicadorConsignacaoRepository.ClienteVendas> todos = new ArrayList<>(repository.vendasPorCliente(f));
        BigDecimal total = ZERO;
        long acertos = 0;
        for (var v : todos) {
            total = total.add(v.vendido());
            acertos += v.acertos();
        }
        todos.sort(Comparator.comparing(IndicadorConsignacaoRepository.ClienteVendas::vendido).reversed());
        List<IndicadorConsignacaoRepository.ClienteVendas> top = todos.size() > 5 ? todos.subList(0, 5) : todos;
        Map<UUID, IndicadorConsignacaoRepository.ClienteNome> nomes = new HashMap<>();
        repository.clientes(top.stream().map(IndicadorConsignacaoRepository.ClienteVendas::clienteId).toList()).forEach(n -> nomes.put(n.id(), n));
        List<IndicadorDTOs.ClienteConcentracao> lista = new ArrayList<>();
        BigDecimal somaTop = ZERO;
        for (var v : top) {
            var n = nomes.get(v.clienteId());
            somaTop = somaTop.add(v.vendido());
            lista.add(new IndicadorDTOs.ClienteConcentracao(v.clienteId(), n == null ? "" : n.nome(), v.vendido().setScale(2), IndicadorCalculo.percentual(v.vendido(), total)));
        }
        return new IndicadorDTOs.Concentracao(lista, IndicadorCalculo.percentual(somaTop, total), total.setScale(2), IndicadorDTOs.Base.acertos(acertos));
    }

    // ---------- produtos e categorias ----------

    @Transactional(readOnly = true)
    public IndicadorDTOs.ProdutosCategorias produtosCategorias(Filtro f, int top) {
        if (top < 1 || top > 50) throw new ItemInvalidoException("O limite deve estar entre 1 e 50.");
        List<IndicadorDTOs.ItemVendido> itens = new ArrayList<>();
        for (var i : repository.itensVendidos(f)) itens.add(new IndicadorDTOs.ItemVendido(i.tipo(), i.id(), i.nome(), i.quantidade(), i.valor().setScale(2)));
        List<IndicadorDTOs.CategoriaVendida> cats = new ArrayList<>();
        for (var c : repository.categoriasVendidas(f)) cats.add(new IndicadorDTOs.CategoriaVendida(c.id(), c.nome(), c.quantidade(), c.valor().setScale(2)));
        var vendas = repository.vendas(f);
        return new IndicadorDTOs.ProdutosCategorias(melhoresPiores(itens, Comparator.comparing(IndicadorDTOs.ItemVendido::valorVendido), top),
                melhoresPiores(cats, Comparator.comparing(IndicadorDTOs.CategoriaVendida::valorVendido), top), IndicadorDTOs.Base.acertos(vendas.acertos()));
    }

    // melhores = maiores valores; piores = menores valores entre os que não estão nos melhores
    static <T> IndicadorDTOs.MelhoresPiores<T> melhoresPiores(List<T> todos, Comparator<T> porValor, int top) {
        List<T> ordenados = new ArrayList<>(todos);
        ordenados.sort(porValor.reversed());
        List<T> melhores = new ArrayList<>(ordenados.subList(0, Math.min(top, ordenados.size())));
        List<T> restantes = new ArrayList<>(ordenados.subList(melhores.size(), ordenados.size()));
        restantes.sort(porValor);
        List<T> piores = new ArrayList<>(restantes.subList(0, Math.min(top, restantes.size())));
        return new IndicadorDTOs.MelhoresPiores<>(melhores, piores);
    }

    // ---------- meios de pagamento ----------

    @Transactional(readOnly = true)
    public IndicadorDTOs.MeiosPagamento meiosPagamento(Filtro f) {
        List<IndicadorConsignacaoRepository.MeioLinha> linhas = new ArrayList<>(repository.meiosPagamento(f));
        BigDecimal total = ZERO, taxa = ZERO;
        long quantidade = 0;
        for (var l : linhas) {
            total = total.add(l.valor());
            taxa = taxa.add(l.taxa());
            quantidade += l.quantidade();
        }
        linhas.sort(Comparator.comparing(IndicadorConsignacaoRepository.MeioLinha::valor).reversed());
        List<IndicadorDTOs.Meio> meios = new ArrayList<>();
        for (var l : linhas) {
            meios.add(new IndicadorDTOs.Meio(l.formaId(), l.nome() == null ? "Crédito do cliente" : l.nome(), l.quantidade(), l.valor().setScale(2), IndicadorCalculo.percentual(l.valor(), total),
                    l.taxa().setScale(2)));
        }
        return new IndicadorDTOs.MeiosPagamento(meios, taxa.setScale(2), IndicadorDTOs.Base.titulos(quantidade));
    }
}
