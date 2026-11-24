package com.manager.app.indicadorconsignacao;

import com.manager.app.indicadorconsignacao.dto.IndicadorDTOs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// regras puras dos indicadores (sem banco): médias ponderadas, faixas de atraso e divisões seguras (sem dados = nulo, nunca divisão por zero)
public final class IndicadorCalculo {

    private IndicadorCalculo() {}

    public static final List<String> FAIXAS = List.of("1-30", "31-60", ">60");

    private static BigDecimal moeda(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    // parte ÷ total × 100 com uma casa; total zero = nulo
    public static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0 || parte == null) return null;
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    // soma ÷ quantidade com duas casas; quantidade zero = nulo
    public static BigDecimal media(BigDecimal soma, long quantidade) {
        if (quantidade <= 0 || soma == null) return null;
        return soma.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    public static String faixa(long diasEmAtraso) {
        if (diasEmAtraso <= 30) return FAIXAS.get(0);
        if (diasEmAtraso <= 60) return FAIXAS.get(1);
        return FAIXAS.get(2);
    }

    // vendas dos acertos feitos e pagos: o desconto do acerto é a comissão (R$), então desconto % e comissão % coincidem; ponderados pelo vendido (Σcomissão ÷ Σvendido)
    public static IndicadorDTOs.Vendas vendas(long acertos, BigDecimal vendido, BigDecimal comissao) {
        BigDecimal pct = percentual(comissao, vendido);
        return new IndicadorDTOs.Vendas(moeda(vendido), moeda(comissao), pct, media(comissao, acertos), pct, media(vendido, acertos), IndicadorDTOs.Base.acertos(acertos));
    }

    // faixas sempre completas e na ordem 1-30, 31-60, >60 (zero quando não há títulos)
    public static IndicadorDTOs.Inadimplencia inadimplencia(Map<String, BigDecimal> valorPorFaixa, Map<String, Long> quantidadePorFaixa) {
        List<IndicadorDTOs.Faixa> faixas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        long qtd = 0;
        for (String f : FAIXAS) {
            BigDecimal v = moeda(valorPorFaixa.get(f));
            long q = quantidadePorFaixa.getOrDefault(f, 0L);
            faixas.add(new IndicadorDTOs.Faixa(f, v, q));
            total = total.add(v);
            qtd += q;
        }
        return new IndicadorDTOs.Inadimplencia(total, qtd, faixas, IndicadorDTOs.Base.titulos(qtd));
    }

    // dias = data do pagamento − vencimento (negativo = adiantado). atraso médio considera só os pagos com atraso (> 0)
    public static IndicadorDTOs.PrazoRecebimento prazo(long pagos, long somaDias, long pagosComAtraso, long somaDiasAtraso, long pagosNoPrazo) {
        if (pagos == 0) return new IndicadorDTOs.PrazoRecebimento(null, null, null, IndicadorDTOs.Base.titulos(0));
        BigDecimal medio = BigDecimal.valueOf(somaDias).divide(BigDecimal.valueOf(pagos), 1, RoundingMode.HALF_UP);
        BigDecimal atraso = pagosComAtraso == 0 ? null : BigDecimal.valueOf(somaDiasAtraso).divide(BigDecimal.valueOf(pagosComAtraso), 1, RoundingMode.HALF_UP);
        BigDecimal noPrazo = BigDecimal.valueOf(pagosNoPrazo).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(pagos), 1, RoundingMode.HALF_UP);
        return new IndicadorDTOs.PrazoRecebimento(medio, atraso, noPrazo, IndicadorDTOs.Base.titulos(pagos));
    }

    // giro: parcela vendida do total que saiu (vendido ÷ (vendido + devolvido)), em valor
    public static IndicadorDTOs.Giro giro(long acertos, BigDecimal vendido, BigDecimal devolvido, long qtdVendida, long qtdDevolvida) {
        BigDecimal total = (vendido == null ? BigDecimal.ZERO : vendido).add(devolvido == null ? BigDecimal.ZERO : devolvido);
        return new IndicadorDTOs.Giro(acertos == 0 ? null : moeda(vendido), acertos == 0 ? null : moeda(devolvido), percentual(vendido, total), qtdVendida, qtdDevolvida,
                IndicadorDTOs.Base.acertos(acertos));
    }

    // meses (yyyy-MM) do período, inclusive os sem dados
    public static List<String> meses(LocalDate inicio, LocalDate fim) {
        List<String> lista = new ArrayList<>();
        YearMonth m = YearMonth.from(inicio);
        YearMonth ultimo = YearMonth.from(fim);
        while (!m.isAfter(ultimo)) {
            lista.add(m.toString());
            m = m.plusMonths(1);
        }
        return lista;
    }
}
