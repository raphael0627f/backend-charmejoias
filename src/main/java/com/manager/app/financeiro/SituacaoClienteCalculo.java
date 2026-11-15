package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.SituacaoClienteDTOs;
import com.manager.app.shared.exception.ItemInvalidoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// faixas de vencimento, atraso e totais da situação do cliente, sem acesso a banco de dados (testáveis em JUnit puro).
// Só entram títulos em aberto (ABERTO ou RECEBIDO_PARCIAL); "valorAberto" = valor - valor recebido.
public final class SituacaoClienteCalculo {

    private SituacaoClienteCalculo() {}

    public record TituloAberto(Long id, Long numero, LocalDate vencimento, BigDecimal valorAberto) {
    }

    public record Resumo(SituacaoClienteDTOs.Faixa emAberto, SituacaoClienteDTOs.Faixa vencido, SituacaoClienteDTOs.Faixa vencemEm7Dias,
                         SituacaoClienteDTOs.Faixa vencemEm30Dias, int diasMaiorAtraso, TituloAberto maisAntigoVencido) {
    }

    // vencido: vencimento anterior a hoje
    public static boolean vencido(LocalDate vencimento, LocalDate hoje) {
        return vencimento.isBefore(hoje);
    }

    // vence nos próximos N dias: de hoje até hoje+N, inclusive (quem vence hoje ainda não está vencido)
    public static boolean venceEm(LocalDate vencimento, LocalDate hoje, int dias) {
        return !vencimento.isBefore(hoje) && !vencimento.isAfter(hoje.plusDays(dias));
    }

    public static int diasDeAtraso(LocalDate vencimento, LocalDate hoje) {
        return vencido(vencimento, hoje) ? (int) ChronoUnit.DAYS.between(vencimento, hoje) : 0;
    }

    public static Resumo resumir(List<TituloAberto> abertos, LocalDate hoje) {
        BigDecimal[] valor = new BigDecimal[4];
        int[] qtd = new int[4];
        for (int i = 0; i < 4; i++) valor[i] = BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        TituloAberto maisAntigo = null;
        for (TituloAberto t : abertos) {
            BigDecimal v = CalculoFinanceiro.arredondar(t.valorAberto());
            if (v.signum() <= 0) continue;
            soma(valor, qtd, 0, v);
            if (vencido(t.vencimento(), hoje)) {
                soma(valor, qtd, 1, v);
                if (maisAntigo == null || t.vencimento().isBefore(maisAntigo.vencimento())
                        || (t.vencimento().equals(maisAntigo.vencimento()) && t.id() < maisAntigo.id())) {
                    maisAntigo = t;
                }
            }
            if (venceEm(t.vencimento(), hoje, 7)) soma(valor, qtd, 2, v);
            if (venceEm(t.vencimento(), hoje, 30)) soma(valor, qtd, 3, v);
        }
        return new Resumo(new SituacaoClienteDTOs.Faixa(valor[0], qtd[0]), new SituacaoClienteDTOs.Faixa(valor[1], qtd[1]),
                new SituacaoClienteDTOs.Faixa(valor[2], qtd[2]), new SituacaoClienteDTOs.Faixa(valor[3], qtd[3]),
                maisAntigo == null ? 0 : diasDeAtraso(maisAntigo.vencimento(), hoje), maisAntigo);
    }

    private static void soma(BigDecimal[] valor, int[] qtd, int i, BigDecimal v) {
        valor[i] = valor[i].add(v);
        qtd[i]++;
    }

    // período de "recebido no período": padrão = últimos 12 meses até hoje; só a data final informada = 12 meses antes dela
    public static LocalDate[] periodo(LocalDate de, LocalDate ate, LocalDate hoje) {
        LocalDate fim = ate == null ? hoje : ate;
        LocalDate inicio = de == null ? fim.minusMonths(12) : de;
        if (inicio.isAfter(fim)) throw new ItemInvalidoException("A data inicial de recebimento não pode ser posterior à data final.");
        return new LocalDate[]{inicio, fim};
    }
}
