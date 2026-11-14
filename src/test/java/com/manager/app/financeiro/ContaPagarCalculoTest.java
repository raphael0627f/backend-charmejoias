package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// somente cálculos: divisão de parcelas, datas, situação exibida e dígitos verificadores da linha digitável
class ContaPagarCalculoTest {

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static BigDecimal soma(List<BigDecimal> l) {
        return l.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- divisão de parcelas ----------

    @Test
    void divisaoEmDozeParcelasColocaOCentavoNaUltima() {
        List<BigDecimal> p = ContaPagarCalculo.dividir(v("1000.00"), 12);
        assertThat(p).hasSize(12);
        assertThat(p.subList(0, 11)).allSatisfy(x -> assertThat(x).isEqualByComparingTo("83.33"));
        assertThat(p.get(11)).isEqualByComparingTo("83.37");
        assertThat(soma(p)).isEqualByComparingTo("1000.00");
    }

    @Test
    void divisaoExataNaoGeraDiferenca() {
        List<BigDecimal> p = ContaPagarCalculo.dividir(v("100.00"), 4);
        assertThat(p).allSatisfy(x -> assertThat(x).isEqualByComparingTo("25.00"));
    }

    @Test
    void somaDasParcelasSempreIgualAoTotalEmVariasCombinacoes() {
        String[] totais = {"0.10", "0.99", "1.00", "10.01", "99.99", "1234.56", "100000.07", "7.77"};
        int[] quantidades = {2, 3, 5, 7, 12, 24, 60, 120};
        for (String t : totais) {
            for (int q : quantidades) {
                if (v(t).compareTo(new BigDecimal("0.01").multiply(BigDecimal.valueOf(q))) < 0) continue;
                List<BigDecimal> p = ContaPagarCalculo.dividir(v(t), q);
                assertThat(soma(p)).as("total %s em %d", t, q).isEqualByComparingTo(t);
                assertThat(p).allSatisfy(x -> assertThat(x.signum()).isPositive());
                assertThat(p.get(q - 1)).as("última parcela %s em %d", t, q).isGreaterThanOrEqualTo(p.get(0));
            }
        }
    }

    @Test
    void valoresPersonalizadosPrecisamSomarOTotalExato() {
        List<BigDecimal> ok = ContaPagarCalculo.validarValores(List.of(v("300.00"), v("200.50"), v("499.50")), v("1000.00"), 3);
        assertThat(soma(ok)).isEqualByComparingTo("1000.00");
        assertThatThrownBy(() -> ContaPagarCalculo.validarValores(List.of(v("300.00"), v("200.50"), v("499.49")), v("1000.00"), 3))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("soma");
        assertThatThrownBy(() -> ContaPagarCalculo.validarValores(List.of(v("500.00"), v("500.00")), v("1000.00"), 3))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("exatamente 3");
        assertThatThrownBy(() -> ContaPagarCalculo.validarValores(List.of(v("1000.00"), v("0.00")), v("1000.00"), 2))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaPagarCalculo.validarValores(List.of(v("500.005"), v("499.995")), v("1000.00"), 2))
                .isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void quantidadeDeParcelasEntreDoisECentoEVinte() {
        ContaPagarCalculo.validarQuantidade(2);
        ContaPagarCalculo.validarQuantidade(120);
        assertThatThrownBy(() -> ContaPagarCalculo.validarQuantidade(1)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaPagarCalculo.validarQuantidade(121)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaPagarCalculo.validarQuantidade(null)).isInstanceOf(ItemInvalidoException.class);
    }

    // ---------- datas ----------

    @Test
    void mensalNoDia31AjustaOFimDoMesSemAcumularOAjuste() {
        List<LocalDate> d = ContaPagarCalculo.datas(IntervaloParcelamento.MENSAL, LocalDate.of(2026, 1, 31), null, 6);
        assertThat(d).containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 30));
    }

    @Test
    void mensalEmAnoBissextoUsaDia29DeFevereiro() {
        List<LocalDate> d = ContaPagarCalculo.datas(IntervaloParcelamento.MENSAL, LocalDate.of(2028, 1, 30), null, 3);
        assertThat(d).containsExactly(LocalDate.of(2028, 1, 30), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 30));
    }

    @Test
    void mensalNoDia30MantemODiaQuandoOMesTem30OuMais() {
        List<LocalDate> d = ContaPagarCalculo.datas(IntervaloParcelamento.MENSAL, LocalDate.of(2026, 11, 30), null, 4);
        assertThat(d).containsExactly(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 30), LocalDate.of(2027, 2, 28));
    }

    @Test
    void quinzenalSomaQuinzeDiasESemanalSomaSete() {
        assertThat(ContaPagarCalculo.datas(IntervaloParcelamento.QUINZENAL, LocalDate.of(2026, 1, 20), null, 3))
                .containsExactly(LocalDate.of(2026, 1, 20), LocalDate.of(2026, 2, 4), LocalDate.of(2026, 2, 19));
        assertThat(ContaPagarCalculo.datas(IntervaloParcelamento.SEMANAL, LocalDate.of(2026, 2, 25), null, 3))
                .containsExactly(LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 11));
    }

    @Test
    void datasPersonalizadasExigemAQuantidadeExata() {
        List<LocalDate> informadas = List.of(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 29), LocalDate.of(2027, 3, 1));
        assertThat(ContaPagarCalculo.datas(IntervaloParcelamento.PERSONALIZADO, null, informadas, 3)).isEqualTo(informadas);
        assertThatThrownBy(() -> ContaPagarCalculo.datas(IntervaloParcelamento.PERSONALIZADO, null, informadas, 4))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("exatamente 4");
        assertThatThrownBy(() -> ContaPagarCalculo.datas(IntervaloParcelamento.MENSAL, null, null, 3)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaPagarCalculo.datas(null, LocalDate.now(), null, 3)).isInstanceOf(ItemInvalidoException.class);
    }

    // ---------- descrição da parcela ----------

    @Test
    void sufixoDaParcelaNaoEDuplicado() {
        assertThat(ContaPagarCalculo.descricaoParcela("Aluguel", 2, 12)).isEqualTo("Aluguel 2/12");
        assertThat(ContaPagarCalculo.descricaoParcela("  Aluguel loja  ", 1, 3)).isEqualTo("Aluguel loja 1/3");
        assertThat(ContaPagarCalculo.descricaoParcela("Aluguel 1/12", 2, 12)).isEqualTo("Aluguel 2/12");
        assertThat(ContaPagarCalculo.descricaoParcela("Compra 3/5", 1, 10)).isEqualTo("Compra 1/10");
        // quem não é sufixo de parcela é preservado
        assertThat(ContaPagarCalculo.descricaoParcela("Nota 12/2026 de materiais", 1, 2)).isEqualTo("Nota 12/2026 de materiais 1/2");
        assertThat(ContaPagarCalculo.descricaoParcela("Pagamento a/b", 1, 2)).isEqualTo("Pagamento a/b 1/2");
    }

    // ---------- situação ----------

    @Test
    void vencidoEhCalculadoParaAbertoEParcialComVencimentoAnteriorAHoje() {
        LocalDate hoje = LocalDate.of(2026, 11, 14);
        assertThat(ContaPagarCalculo.situacaoExibicao(SituacaoContaPagar.ABERTO, hoje.minusDays(1), hoje)).isEqualTo(SituacaoExibicaoContaPagar.VENCIDO);
        assertThat(ContaPagarCalculo.situacaoExibicao(SituacaoContaPagar.PAGO_PARCIAL, hoje.minusDays(30), hoje)).isEqualTo(SituacaoExibicaoContaPagar.VENCIDO);
        // vence hoje ainda não está vencido
        assertThat(ContaPagarCalculo.situacaoExibicao(SituacaoContaPagar.ABERTO, hoje, hoje)).isEqualTo(SituacaoExibicaoContaPagar.ABERTO);
        assertThat(ContaPagarCalculo.situacaoExibicao(SituacaoContaPagar.ABERTO, hoje.plusDays(1), hoje)).isEqualTo(SituacaoExibicaoContaPagar.ABERTO);
        // pago e cancelado nunca viram vencidos
        assertThat(ContaPagarCalculo.situacaoExibicao(SituacaoContaPagar.PAGO, hoje.minusDays(90), hoje)).isEqualTo(SituacaoExibicaoContaPagar.PAGO);
        assertThat(ContaPagarCalculo.situacaoExibicao(SituacaoContaPagar.CANCELADO, hoje.minusDays(90), hoje)).isEqualTo(SituacaoExibicaoContaPagar.CANCELADO);
    }

    @Test
    void situacaoGravadaSegueOValorPagoEValorAbertoNuncaFicaNegativo() {
        assertThat(ContaPagarCalculo.situacaoPara(v("100.00"), v("0.00"))).isEqualTo(SituacaoContaPagar.ABERTO);
        assertThat(ContaPagarCalculo.situacaoPara(v("100.00"), v("40.00"))).isEqualTo(SituacaoContaPagar.PAGO_PARCIAL);
        assertThat(ContaPagarCalculo.situacaoPara(v("100.00"), v("100.00"))).isEqualTo(SituacaoContaPagar.PAGO);
        assertThat(ContaPagarCalculo.valorAberto(SituacaoContaPagar.PAGO_PARCIAL, v("100.00"), v("40.00"))).isEqualByComparingTo("60.00");
        assertThat(ContaPagarCalculo.valorAberto(SituacaoContaPagar.CANCELADO, v("100.00"), v("0.00"))).isEqualByComparingTo("0.00");
        assertThat(ContaPagarCalculo.valorAberto(SituacaoContaPagar.PAGO, v("100.00"), v("100.00"))).isEqualByComparingTo("0.00");
    }

    @Test
    void competenciaPadraoEOMesDoVencimentoEValidaFormato() {
        assertThat(ContaPagarCalculo.competenciaPadrao(LocalDate.of(2026, 12, 31))).isEqualTo("2026-12");
        assertThat(ContaPagarCalculo.validarCompetencia(" 2026-03 ")).isEqualTo("2026-03");
        assertThatThrownBy(() -> ContaPagarCalculo.validarCompetencia("03/2026")).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> ContaPagarCalculo.validarCompetencia("2026-13")).isInstanceOf(ItemInvalidoException.class);
    }

    // ---------- linha digitável ----------

    // monta uma linha de boleto de 47 dígitos com os DVs calculados de forma independente (laços simples, sem reutilizar a classe testada)
    private static String boletoValido(String banco4, String livre25, String fatorValor14) {
        String c1 = banco4 + livre25.substring(0, 5);
        String c2 = livre25.substring(5, 15);
        String c3 = livre25.substring(15, 25);
        String semDv = banco4 + fatorValor14 + livre25;
        int soma = 0;
        int peso = 2;
        for (int i = semDv.length() - 1; i >= 0; i--) {
            soma += (semDv.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int dv = 11 - soma % 11;
        if (dv == 0 || dv == 10 || dv == 11) dv = 1;
        return c1 + mod10(c1) + c2 + mod10(c2) + c3 + mod10(c3) + dv + fatorValor14;
    }

    private static int mod10(String s) {
        int soma = 0;
        for (int i = 0; i < s.length(); i++) {
            int posicaoDaDireita = s.length() - i;
            int produto = (s.charAt(i) - '0') * (posicaoDaDireita % 2 == 1 ? 2 : 1);
            soma += produto / 10 + produto % 10;
        }
        return (10 - soma % 10) % 10;
    }

    @Test
    void boletoBancarioComDigitosVerificadoresCorretosEAceito() {
        String linha = boletoValido("2379", "5040041000000190000000042", "85270000010000");
        assertThat(linha).hasSize(47);
        assertThat(LinhaDigitavel.normalizar(linha)).isEqualTo(linha);
        // formatada com pontos e espaços
        String formatada = linha.substring(0, 5) + "." + linha.substring(5, 10) + " " + linha.substring(10, 15) + "." + linha.substring(15, 21)
                + " " + linha.substring(21, 26) + "." + linha.substring(26, 32) + " " + linha.charAt(32) + " " + linha.substring(33);
        assertThat(LinhaDigitavel.normalizar(formatada)).isEqualTo(linha);
    }

    @Test
    void boletoBancarioComQualquerDigitoAlteradoEhRecusado() {
        String linha = boletoValido("0019", "0000901234567890123456789", "10000000012345");
        assertThat(LinhaDigitavel.normalizar(linha)).isNotNull();
        for (int i = 0; i < linha.length(); i++) {
            char original = linha.charAt(i);
            char trocado = original == '9' ? '8' : (char) (original + 1);
            String alterada = linha.substring(0, i) + trocado + linha.substring(i + 1);
            // trocar um dígito do campo de valor/fator ou do campo livre sempre invalida o DV geral ou o do campo
            assertThatThrownBy(() -> LinhaDigitavel.normalizar(alterada)).as("posição %d", i).isInstanceOf(ItemInvalidoException.class);
        }
    }

    private static String arrecadacao(String identificadorModulo, boolean modulo10) {
        StringBuilder linha = new StringBuilder();
        String[] campos = {"8" + identificadorModulo + "6" + "00000012", "34560000000", "11111111111", "22222222222"};
        for (int b = 0; b < 4; b++) {
            String campo = b == 0 ? "8" + "1" + (modulo10 ? "6" : "8") + "00000012" : campos[b];
            campo = campo.substring(0, 11);
            int dv;
            if (modulo10) {
                dv = mod10(campo);
            } else {
                int soma = 0;
                int peso = 2;
                for (int i = campo.length() - 1; i >= 0; i--) {
                    soma += (campo.charAt(i) - '0') * peso;
                    peso = peso == 9 ? 2 : peso + 1;
                }
                int resto = soma % 11;
                dv = resto == 0 || resto == 1 ? 0 : 11 - resto;
            }
            linha.append(campo).append(dv);
        }
        return linha.toString();
    }

    @Test
    void arrecadacaoValidaEmModulo10EModulo11() {
        String m10 = arrecadacao("1", true);
        String m11 = arrecadacao("1", false);
        assertThat(m10).hasSize(48).startsWith("8");
        assertThat(LinhaDigitavel.normalizar(m10)).isEqualTo(m10);
        assertThat(LinhaDigitavel.normalizar(m11)).isEqualTo(m11);
        String quebrada = m10.substring(0, 11) + (m10.charAt(11) == '9' ? '8' : (char) (m10.charAt(11) + 1)) + m10.substring(12);
        assertThatThrownBy(() -> LinhaDigitavel.normalizar(quebrada)).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("dígito verificador");
    }

    @Test
    void linhaDigitavelRecusaCaracteresETamanhosInvalidos() {
        assertThat(LinhaDigitavel.normalizar(null)).isNull();
        assertThat(LinhaDigitavel.normalizar("   ")).isNull();
        assertThatThrownBy(() -> LinhaDigitavel.normalizar("2379A05040")).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("apenas números");
        assertThatThrownBy(() -> LinhaDigitavel.normalizar("23790-50400")).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> LinhaDigitavel.normalizar("12345")).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("47 dígitos");
        assertThatThrownBy(() -> LinhaDigitavel.normalizar("9".repeat(48))).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> LinhaDigitavel.normalizar("1".repeat(49))).isInstanceOf(ItemInvalidoException.class);
    }
}
