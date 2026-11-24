package com.manager.app.fluxocaixa;

import com.manager.app.fluxocaixa.dto.FluxoCaixaDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// regras puras do fluxo de caixa (sem banco): períodos, séries por visão, saldos acumulados e resumo
public final class FluxoCaixaCalculo {

    private FluxoCaixaCalculo() {}

    public static final int MAXIMO_DIAS = 366;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MA = DateTimeFormatter.ofPattern("MM/yyyy");

    public enum Visao {
        DIARIO, SEMANAL, MENSAL;

        public static Visao de(String texto) {
            if (texto == null || texto.isBlank()) return DIARIO;
            try {
                return valueOf(texto.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ItemInvalidoException("Visão inválida. Use DIARIO, SEMANAL ou MENSAL.");
            }
        }
    }

    // valores agregados por dia (o dia do vencimento no previsto e a data do recebimento/pagamento no realizado)
    public record Dados(Map<LocalDate, BigDecimal> entradasRealizadas, Map<LocalDate, BigDecimal> saidasRealizadas, Map<LocalDate, BigDecimal> entradasPrevistas,
                        Map<LocalDate, BigDecimal> saidasPrevistas, Map<LocalDate, BigDecimal> saidasPrevistasEstimadas,
                        Map<LocalDate, BigDecimal> movimentacaoLiquida, BigDecimal atrasadosReceberValor, long atrasadosReceberQuantidade,
                        BigDecimal atrasadosPagarValor, long atrasadosPagarQuantidade) {
    }

    public record Faixa(String id, String rotulo, LocalDate de, LocalDate ate) {
    }

    public record Resultado(List<FluxoCaixaDTOs.Ponto> serie, FluxoCaixaDTOs.Resumo resumo) {
    }

    public static void validarIntervalo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) throw new ItemInvalidoException("Informe o período.");
        if (inicio.isAfter(fim)) throw new ItemInvalidoException("A data inicial não pode ser posterior à data final.");
        if (inicio.plusDays(MAXIMO_DIAS).isBefore(fim)) throw new ItemInvalidoException("O período não pode ser maior que " + MAXIMO_DIAS + " dias.");
    }

    // chave do período a que o dia pertence (dia; segunda-feira da semana; primeiro dia do mês)
    public static LocalDate chave(LocalDate dia, Visao visao) {
        return switch (visao) {
            case DIARIO -> dia;
            case SEMANAL -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MENSAL -> dia.withDayOfMonth(1);
        };
    }

    private static String id(LocalDate chave, Visao visao) {
        return visao == Visao.MENSAL ? YearMonth.from(chave).toString() : chave.toString();
    }

    public static Faixa faixa(LocalDate chave, Visao visao, LocalDate inicio, LocalDate fim) {
        LocalDate fimDoPeriodo = switch (visao) {
            case DIARIO -> chave;
            case SEMANAL -> chave.plusDays(6);
            case MENSAL -> chave.with(TemporalAdjusters.lastDayOfMonth());
        };
        LocalDate de = chave.isBefore(inicio) ? inicio : chave;
        LocalDate ate = fimDoPeriodo.isAfter(fim) ? fim : fimDoPeriodo;
        String rotulo = switch (visao) {
            case DIARIO -> de.format(DM);
            case SEMANAL -> de.format(DM) + " a " + ate.format(DM);
            case MENSAL -> chave.format(MA);
        };
        return new Faixa(id(chave, visao), rotulo, de, ate);
    }

    public static List<Faixa> faixas(LocalDate inicio, LocalDate fim, Visao visao) {
        List<Faixa> lista = new ArrayList<>();
        LocalDate chave = chave(inicio, visao);
        while (!chave.isAfter(fim)) {
            lista.add(faixa(chave, visao, inicio, fim));
            chave = switch (visao) {
                case DIARIO -> chave.plusDays(1);
                case SEMANAL -> chave.plusWeeks(1);
                case MENSAL -> chave.plusMonths(1);
            };
        }
        return lista;
    }

    // período recebido no detalhe ("2026-11-24", "2026-11-23" da semana, "2026-11"): devolve o intervalo recortado pelo período consultado
    public static Faixa faixaDoTexto(String texto, Visao visao, LocalDate inicio, LocalDate fim) {
        if (texto == null || texto.isBlank()) throw new ItemInvalidoException("Informe o período a detalhar.");
        try {
            LocalDate chave = visao == Visao.MENSAL ? YearMonth.parse(texto.trim()).atDay(1) : chave(LocalDate.parse(texto.trim()), visao);
            Faixa f = faixa(chave, visao, inicio, fim);
            if (f.de().isAfter(f.ate())) throw new ItemInvalidoException("O período informado está fora do intervalo consultado.");
            return f;
        } catch (DateTimeParseException e) {
            throw new ItemInvalidoException("Período inválido para a visão " + visao.name() + ".");
        }
    }

    private static BigDecimal soma(Map<LocalDate, BigDecimal> mapa, Faixa f) {
        BigDecimal total = ZERO;
        if (mapa == null) return total;
        for (Map.Entry<LocalDate, BigDecimal> e : mapa.entrySet()) {
            if (!e.getKey().isBefore(f.de()) && !e.getKey().isAfter(f.ate())) total = total.add(e.getValue());
        }
        return total;
    }

    private static BigDecimal r(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    // saldoInicial nulo = saldos não exibidos (sem permissão ou com filtro de título): campos de saldo ficam nulos.
    // Atrasados são a posição de HOJE: aparecem no período que contém hoje (se houver) e nunca entram no previsto nem no acumulado.
    public static Resultado calcular(Dados d, LocalDate inicio, LocalDate fim, Visao visao, LocalDate hoje, BigDecimal saldoInicial) {
        boolean saldos = saldoInicial != null;
        List<FluxoCaixaDTOs.Ponto> serie = new ArrayList<>();
        BigDecimal acumuladoReal = saldos ? r(saldoInicial) : null;
        BigDecimal previstoAcumulado = ZERO;
        BigDecimal entReal = ZERO, saiReal = ZERO, entPrev = ZERO, saiPrev = ZERO, saiPrevEst = ZERO, outras = ZERO;
        for (Faixa f : faixas(inicio, fim, visao)) {
            BigDecimal er = soma(d.entradasRealizadas(), f), sr = soma(d.saidasRealizadas(), f);
            BigDecimal ep = soma(d.entradasPrevistas(), f), sp = soma(d.saidasPrevistas(), f);
            BigDecimal mov = soma(d.movimentacaoLiquida(), f);
            boolean temHoje = !hoje.isBefore(f.de()) && !hoje.isAfter(f.ate());
            BigDecimal atrasR = temHoje ? r(d.atrasadosReceberValor()) : ZERO;
            BigDecimal atrasP = temHoje ? r(d.atrasadosPagarValor()) : ZERO;
            previstoAcumulado = previstoAcumulado.add(ep).subtract(sp);
            BigDecimal acumReal = null, acumProj = null;
            if (saldos) {
                acumuladoReal = acumuladoReal.add(mov);
                acumReal = acumuladoReal;
                acumProj = acumuladoReal.add(previstoAcumulado);
                outras = outras.add(mov.subtract(er.subtract(sr)));
            }
            entReal = entReal.add(er);
            saiReal = saiReal.add(sr);
            entPrev = entPrev.add(ep);
            saiPrev = saiPrev.add(sp);
            saiPrevEst = saiPrevEst.add(soma(d.saidasPrevistasEstimadas(), f));
            serie.add(new FluxoCaixaDTOs.Ponto(f.id(), f.rotulo(), er, ep, sr, sp, atrasR, atrasP, acumReal, acumProj));
        }
        BigDecimal finalReal = saldos ? acumuladoReal : null;
        BigDecimal finalProj = saldos ? acumuladoReal.add(previstoAcumulado) : null;
        FluxoCaixaDTOs.Resumo resumo = new FluxoCaixaDTOs.Resumo(saldos ? r(saldoInicial) : null, entReal, entPrev, saiReal, saiPrev, saiPrevEst,
                saldos ? outras : null, entReal.subtract(saiReal), entPrev.subtract(saiPrev), finalReal, finalProj,
                new FluxoCaixaDTOs.Atrasado(r(d.atrasadosReceberValor()), d.atrasadosReceberQuantidade()),
                new FluxoCaixaDTOs.Atrasado(r(d.atrasadosPagarValor()), d.atrasadosPagarQuantidade()));
        return new Resultado(serie, resumo);
    }

    // percentual com uma casa; base zero devolve zero (nunca divide por zero)
    public static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO.setScale(1);
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    // mapa dia -> valor somando linhas repetidas do mesmo dia
    public static Map<LocalDate, BigDecimal> mapa(List<Map.Entry<LocalDate, BigDecimal>> linhas) {
        Map<LocalDate, BigDecimal> m = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> l : linhas) m.merge(l.getKey(), l.getValue(), BigDecimal::add);
        return m;
    }
}
