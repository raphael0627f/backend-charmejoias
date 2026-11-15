package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

// cálculos e regras do Contas a Pagar sem acesso a banco de dados (testáveis em JUnit puro)
public final class ContaPagarCalculo {

    public static final int PARCELAS_MINIMO = 2;
    public static final int PARCELAS_MAXIMO = 120;

    private ContaPagarCalculo() {}

    // valores iguais arredondados para baixo em 2 casas; a diferença de centavos fica na ÚLTIMA parcela
    public static List<BigDecimal> dividir(BigDecimal total, int quantidade) {
        BigDecimal t = CalculoFinanceiro.arredondar(total);
        BigDecimal base = t.divide(BigDecimal.valueOf(quantidade), CalculoFinanceiro.ESCALA, RoundingMode.DOWN);
        if (base.signum() <= 0) {
            throw new ItemInvalidoException("O valor total é insuficiente para ser dividido em " + quantidade + " parcelas.");
        }
        List<BigDecimal> valores = new ArrayList<>();
        for (int i = 0; i < quantidade - 1; i++) valores.add(base);
        valores.add(t.subtract(base.multiply(BigDecimal.valueOf(quantidade - 1L))));
        return valores;
    }

    // valores personalizados: um por parcela, todos > 0 com até 2 casas, e soma exatamente igual ao total
    public static List<BigDecimal> validarValores(List<BigDecimal> valores, BigDecimal total, int quantidade) {
        if (valores.size() != quantidade) {
            throw new ItemInvalidoException("Informe exatamente " + quantidade + " valores de parcela.");
        }
        List<BigDecimal> resultado = new ArrayList<>();
        BigDecimal soma = BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        for (BigDecimal v : valores) {
            BigDecimal ok = CalculoFinanceiro.validarValorPositivo(v);
            resultado.add(ok);
            soma = soma.add(ok);
        }
        if (soma.compareTo(CalculoFinanceiro.arredondar(total)) != 0) {
            throw new ItemInvalidoException("A soma dos valores das parcelas deve ser igual ao valor total.");
        }
        return resultado;
    }

    public static void validarQuantidade(Integer quantidade) {
        if (quantidade == null || quantidade < PARCELAS_MINIMO || quantidade > PARCELAS_MAXIMO) {
            throw new ItemInvalidoException("A quantidade de parcelas deve estar entre " + PARCELAS_MINIMO + " e " + PARCELAS_MAXIMO + ".");
        }
    }

    // vencimentos das parcelas; mensal parte sempre da data original (31/01 -> 28/02 -> 31/03, sem acumular o ajuste)
    public static List<LocalDate> datas(IntervaloParcelamento intervalo, LocalDate primeiro, List<LocalDate> personalizadas, int quantidade) {
        if (intervalo == null) throw new ItemInvalidoException("Informe o intervalo do parcelamento.");
        List<LocalDate> datas = new ArrayList<>();
        if (intervalo == IntervaloParcelamento.PERSONALIZADO) {
            if (personalizadas == null || personalizadas.size() != quantidade) {
                throw new ItemInvalidoException("Informe exatamente " + quantidade + " datas de vencimento.");
            }
            for (LocalDate d : personalizadas) {
                if (d == null) throw new ItemInvalidoException("Todas as datas de vencimento devem ser informadas.");
                datas.add(d);
            }
            return datas;
        }
        if (primeiro == null) throw new ItemInvalidoException("Informe o primeiro vencimento.");
        for (int i = 0; i < quantidade; i++) {
            datas.add(switch (intervalo) {
                case MENSAL -> primeiro.plusMonths(i);
                case QUINZENAL -> primeiro.plusDays(15L * i);
                case SEMANAL -> primeiro.plusWeeks(i);
                default -> primeiro;
            });
        }
        return datas;
    }

    // "Aluguel" -> "Aluguel 2/12"; se a descrição já termina em "n/N", o sufixo é substituído (não duplicado)
    public static String descricaoParcela(String descricao, int numero, int total) {
        return semSufixoDeParcela(descricao) + " " + numero + "/" + total;
    }

    public static String semSufixoDeParcela(String descricao) {
        String d = descricao == null ? "" : descricao.trim();
        int espaco = d.lastIndexOf(' ');
        if (espaco < 0) return d;
        String ultimo = d.substring(espaco + 1);
        int barra = ultimo.indexOf('/');
        if (barra > 0 && barra < ultimo.length() - 1 && soDigitos(ultimo.substring(0, barra)) && soDigitos(ultimo.substring(barra + 1))) {
            return d.substring(0, espaco).trim();
        }
        return d;
    }

    private static boolean soDigitos(String texto) {
        if (texto.isEmpty()) return false;
        for (int i = 0; i < texto.length(); i++) {
            if (!Character.isDigit(texto.charAt(i))) return false;
        }
        return true;
    }

    // situação mostrada: aberto/parcial com vencimento anterior a hoje vira VENCIDO (calculada, nunca gravada)
    public static SituacaoExibicaoContaPagar situacaoExibicao(SituacaoContaPagar situacao, LocalDate vencimento, LocalDate hoje) {
        if ((situacao == SituacaoContaPagar.ABERTO || situacao == SituacaoContaPagar.PAGO_PARCIAL) && vencimento.isBefore(hoje)) {
            return SituacaoExibicaoContaPagar.VENCIDO;
        }
        return SituacaoExibicaoContaPagar.valueOf(situacao.name());
    }

    // situação gravada a partir do valor e do pago (não trata CANCELADO)
    public static SituacaoContaPagar situacaoPara(BigDecimal valor, BigDecimal valorPago) {
        BigDecimal pago = CalculoFinanceiro.arredondar(valorPago);
        if (pago.signum() <= 0) return SituacaoContaPagar.ABERTO;
        return pago.compareTo(CalculoFinanceiro.arredondar(valor)) >= 0 ? SituacaoContaPagar.PAGO : SituacaoContaPagar.PAGO_PARCIAL;
    }

    // valor em aberto: cancelado não tem; nunca negativo
    public static BigDecimal valorAberto(SituacaoContaPagar situacao, BigDecimal valor, BigDecimal valorPago) {
        if (situacao == SituacaoContaPagar.CANCELADO) return BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        BigDecimal aberto = CalculoFinanceiro.arredondar(valor).subtract(CalculoFinanceiro.arredondar(valorPago));
        return aberto.signum() < 0 ? BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA) : aberto;
    }

    // parte do valor em aberto que ainda é estimativa (título estimado, aberto ou parcial); os cancelados não entram
    public static BigDecimal valorAbertoEstimado(SituacaoContaPagar situacao, boolean estimado, BigDecimal valor, BigDecimal valorPago) {
        return estimado ? valorAberto(situacao, valor, valorPago) : BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
    }

    // conta na quantidade de estimados: estimado e não cancelado
    public static boolean contaComoEstimado(SituacaoContaPagar situacao, boolean estimado) {
        return estimado && situacao != SituacaoContaPagar.CANCELADO;
    }

    public static String competenciaPadrao(LocalDate vencimento) {
        return YearMonth.from(vencimento).toString();
    }

    public static String validarCompetencia(String competencia) {
        try {
            return YearMonth.parse(competencia.trim()).toString();
        } catch (RuntimeException e) {
            throw new ItemInvalidoException("A competência deve estar no formato aaaa-mm (exemplo: 2026-11).");
        }
    }
}
