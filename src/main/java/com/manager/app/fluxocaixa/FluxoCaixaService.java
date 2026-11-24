package com.manager.app.fluxocaixa;

import com.manager.app.financeiro.SaldoVisibilidade;
import com.manager.app.fluxocaixa.FluxoCaixaCalculo.Visao;
import com.manager.app.fluxocaixa.FluxoCaixaRepository.Lado;
import com.manager.app.fluxocaixa.dto.FluxoCaixaDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FluxoCaixaService {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    static final String MOTIVO_FILTRO = "Os saldos não são exibidos quando há filtro de categoria, centro de custo, cliente ou favorecido.";

    private final FluxoCaixaRepository repository;
    private final SaldoVisibilidade saldoVisibilidade;

    public static FluxoCaixaFiltro normalizar(FluxoCaixaFiltro f, LocalDate hoje) {
        LocalDate inicio = f.inicio() == null ? hoje.withDayOfMonth(1) : f.inicio();
        LocalDate fim = f.fim() == null ? (f.inicio() == null ? hoje.withDayOfMonth(hoje.lengthOfMonth()) : inicio.withDayOfMonth(inicio.lengthOfMonth())) : f.fim();
        FluxoCaixaCalculo.validarIntervalo(inicio, fim);
        return f.comPeriodo(inicio, fim);
    }

    private static <K, V> Map<K, V> vazio() {
        return new LinkedHashMap<>();
    }

    @Transactional(readOnly = true)
    public FluxoCaixaDTOs.Resposta resumo(FluxoCaixaFiltro filtroBruto, Visao visao) {
        return resumo(filtroBruto, visao, LocalDate.now());
    }

    FluxoCaixaDTOs.Resposta resumo(FluxoCaixaFiltro filtroBruto, Visao visao, LocalDate hoje) {
        FluxoCaixaFiltro f = normalizar(filtroBruto, hoje);
        LocalDate inicio = f.inicio(), fim = f.fim();
        boolean receber = f.incluiReceber();
        LocalDate previstoDe = inicio.isAfter(hoje) ? inicio : hoje;
        boolean temPrevisto = !previstoDe.isAfter(fim);

        Map<LocalDate, BigDecimal> entReal = receber ? repository.realizadoReceber(f, inicio, fim) : vazio();
        Map<LocalDate, BigDecimal> saiReal = repository.realizadoPagar(f, inicio, fim);
        Map<LocalDate, BigDecimal> entPrev = receber && temPrevisto ? repository.previstoReceber(f, previstoDe, fim) : vazio();
        FluxoCaixaRepository.PrevistoPagar pagarPrev = temPrevisto ? repository.previstoPagar(f, previstoDe, fim)
                : new FluxoCaixaRepository.PrevistoPagar(vazio(), vazio());
        FluxoCaixaRepository.AtrasadoLinha atrasR = receber ? repository.atrasados(Lado.RECEBER, f, hoje) : new FluxoCaixaRepository.AtrasadoLinha(ZERO, 0);
        FluxoCaixaRepository.AtrasadoLinha atrasP = repository.atrasados(Lado.PAGAR, f, hoje);

        boolean saldos = saldoVisibilidade.podeVerSaldo() && !f.temFiltroDeTitulo();
        BigDecimal saldoInicial = saldos ? repository.saldoAte(f.bancoIds(), inicio) : null;
        Map<LocalDate, BigDecimal> mov = saldos ? repository.movimentacaoLiquida(f.bancoIds(), inicio, fim) : vazio();

        FluxoCaixaCalculo.Dados dados = new FluxoCaixaCalculo.Dados(entReal, saiReal, entPrev, pagarPrev.total(), pagarPrev.estimado(), mov,
                atrasR.valor(), atrasR.quantidade(), atrasP.valor(), atrasP.quantidade());
        FluxoCaixaCalculo.Resultado r = FluxoCaixaCalculo.calcular(dados, inicio, fim, visao, hoje, saldoInicial);

        String motivo = !saldoVisibilidade.podeVerSaldo() ? null : (f.temFiltroDeTitulo() ? MOTIVO_FILTRO : null);
        return new FluxoCaixaDTOs.Resposta(new FluxoCaixaDTOs.Periodo(inicio, fim, visao.name(), hoje), saldos, motivo, !f.bancoIds().isEmpty(), r.resumo(), r.serie(),
                saldos ? porBanco(f) : null, categorias(f, hoje, previstoDe, temPrevisto), repository.maiorVencimentoPagar());
    }

    private List<FluxoCaixaDTOs.PorBanco> porBanco(FluxoCaixaFiltro f) {
        LocalDate inicio = f.inicio(), fim = f.fim();
        Map<Long, BigDecimal> ini = repository.saldoPorBancoAte(f.bancoIds(), inicio);
        Map<Long, BigDecimal> mov = repository.movimentacaoPorBanco(f.bancoIds(), inicio, fim);
        Map<Long, BigDecimal> ent = f.incluiReceber() ? repository.realizadoPorBanco(Lado.RECEBER, f, inicio, fim) : vazio();
        Map<Long, BigDecimal> sai = repository.realizadoPorBanco(Lado.PAGAR, f, inicio, fim);
        List<FluxoCaixaDTOs.PorBanco> lista = new ArrayList<>();
        for (FluxoCaixaRepository.BancoLinha b : repository.bancos(f.bancoIds())) {
            BigDecimal si = ini.getOrDefault(b.id(), ZERO), m = mov.getOrDefault(b.id(), ZERO);
            BigDecimal e = ent.getOrDefault(b.id(), ZERO), s = sai.getOrDefault(b.id(), ZERO);
            if (f.bancoIds().isEmpty() && si.signum() == 0 && m.signum() == 0 && e.signum() == 0 && s.signum() == 0) continue;
            lista.add(new FluxoCaixaDTOs.PorBanco(b.id(), b.nome(), si, e, s, m.subtract(e.subtract(s)), si.add(m)));
        }
        return lista;
    }

    private FluxoCaixaDTOs.Categorias categorias(FluxoCaixaFiltro f, LocalDate hoje, LocalDate previstoDe, boolean temPrevisto) {
        List<FluxoCaixaDTOs.PorCategoria> receitas = f.incluiReceber() ? categoriasDoLado(Lado.RECEBER, f, hoje, previstoDe, temPrevisto) : List.of();
        return new FluxoCaixaDTOs.Categorias(receitas, categoriasDoLado(Lado.PAGAR, f, hoje, previstoDe, temPrevisto));
    }

    // acumulador por categoria: colunas 0 realizado, 1 previsto, 2 atrasado
    private record Acum(String nome, String pai, BigDecimal[] v) {
        static Acum de(FluxoCaixaRepository.CategoriaLinha l) {
            return new Acum(l.nome(), l.pai(), new BigDecimal[]{ZERO, ZERO, ZERO});
        }
    }

    private List<FluxoCaixaDTOs.PorCategoria> categoriasDoLado(Lado lado, FluxoCaixaFiltro f, LocalDate hoje, LocalDate previstoDe, boolean temPrevisto) {
        Map<Long, Acum> mapa = new LinkedHashMap<>();
        juntar(mapa, repository.categoriasRealizado(lado, f, f.inicio(), f.fim()), 0);
        if (temPrevisto) juntar(mapa, repository.categoriasAberto(lado, f, previstoDe, f.fim(), null), 1);
        juntar(mapa, repository.categoriasAberto(lado, f, null, null, hoje), 2);
        BigDecimal total = ZERO;
        for (Acum a : mapa.values()) total = total.add(a.v()[0]).add(a.v()[1]).add(a.v()[2]);
        List<FluxoCaixaDTOs.PorCategoria> lista = new ArrayList<>();
        for (Map.Entry<Long, Acum> e : mapa.entrySet()) {
            BigDecimal[] v = e.getValue().v();
            BigDecimal soma = v[0].add(v[1]).add(v[2]);
            lista.add(new FluxoCaixaDTOs.PorCategoria(e.getKey(), e.getValue().nome(), e.getValue().pai(), v[0], v[1], v[2], FluxoCaixaCalculo.percentual(soma, total)));
        }
        lista.sort(Comparator.comparing((FluxoCaixaDTOs.PorCategoria c) -> c.realizado().add(c.previsto()).add(c.atrasado())).reversed());
        return lista;
    }

    private static void juntar(Map<Long, Acum> destino, List<FluxoCaixaRepository.CategoriaLinha> linhas, int coluna) {
        for (FluxoCaixaRepository.CategoriaLinha l : linhas) {
            Acum a = destino.computeIfAbsent(l.id(), k -> Acum.de(l));
            a.v()[coluna] = a.v()[coluna].add(l.valor());
        }
    }

    // ---------- detalhe ----------

    private static final Set<String> TIPOS = Set.of("ENTRADA", "SAIDA");
    private static final Set<String> SITUACOES = Set.of("REALIZADO", "PREVISTO", "ATRASADO");

    @Transactional(readOnly = true)
    public FluxoCaixaDTOs.Detalhe detalhe(FluxoCaixaFiltro filtroBruto, Visao visao, String periodo, String tipoTexto, String situacaoTexto, String sort, int page, int size) {
        return detalhe(filtroBruto, visao, periodo, tipoTexto, situacaoTexto, sort, page, size, LocalDate.now());
    }

    FluxoCaixaDTOs.Detalhe detalhe(FluxoCaixaFiltro filtroBruto, Visao visao, String periodo, String tipoTexto, String situacaoTexto, String sort, int page, int size, LocalDate hoje) {
        if (page < 0) throw new ItemInvalidoException("A página deve ser maior ou igual a zero.");
        if (size < 1 || size > TAMANHO_MAXIMO) throw new ItemInvalidoException("O tamanho da página deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        String tipo = tipoTexto == null ? "" : tipoTexto.trim().toUpperCase(Locale.ROOT);
        String situacao = situacaoTexto == null ? "" : situacaoTexto.trim().toUpperCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) throw new ItemInvalidoException("Tipo inválido. Use ENTRADA ou SAIDA.");
        if (!SITUACOES.contains(situacao)) throw new ItemInvalidoException("Situação inválida. Use REALIZADO, PREVISTO ou ATRASADO.");
        FluxoCaixaFiltro f = normalizar(filtroBruto, hoje);
        String campo = "data";
        boolean desc = situacao.equals("REALIZADO");
        if (sort != null && !sort.isBlank()) {
            String[] partes = sort.split(",", 2);
            campo = partes[0].trim();
            if (!FluxoCaixaRepository.ORDENACOES.containsKey(campo)) throw new ItemInvalidoException("Campo de ordenação inválido.");
            if (partes.length > 1 && !partes[1].isBlank()) {
                String dir = partes[1].trim();
                if (!dir.equalsIgnoreCase("asc") && !dir.equalsIgnoreCase("desc")) throw new ItemInvalidoException("Direção inválida. Use asc ou desc.");
                desc = dir.equalsIgnoreCase("desc");
            } else {
                desc = false;
            }
        }
        LocalDate de = f.inicio(), ate = f.fim();
        if (!situacao.equals("ATRASADO")) {
            FluxoCaixaCalculo.Faixa faixa = FluxoCaixaCalculo.faixaDoTexto(periodo, visao, f.inicio(), f.fim());
            de = faixa.de();
            ate = faixa.ate();
            if (situacao.equals("PREVISTO") && de.isBefore(hoje)) de = hoje;
            if (de.isAfter(ate)) return vazio(page, size);
        }
        Lado lado = tipo.equals("ENTRADA") ? Lado.RECEBER : Lado.PAGAR;
        if (lado == Lado.RECEBER && !f.incluiReceber()) return vazio(page, size);
        return repository.detalhe(lado, situacao, f, de, ate, hoje, campo, desc, page, size);
    }

    private static FluxoCaixaDTOs.Detalhe vazio(int page, int size) {
        return new FluxoCaixaDTOs.Detalhe(page, size, 0, 0, ZERO, List.of());
    }
}
