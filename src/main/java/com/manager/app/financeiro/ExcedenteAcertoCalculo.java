package com.manager.app.financeiro;

import java.math.BigDecimal;

// REGRA DE CÁLCULO DO EXCEDENTE, isolada para ser trocada com facilidade (sem acesso a banco, testável em JUnit puro).
// Decisão do usuário: a base é o que o cliente EFETIVAMENTE PAGOU (valor recebido) dos títulos do pré-acerto da consignação.
// O tratamento do título do pré-acerto ainda em aberto e maior que o acerto está pendente de decisão do usuário (ver bloqueioNaConfirmacao).
public final class ExcedenteAcertoCalculo {

    private ExcedenteAcertoCalculo() {}

    // excedente = max(0, recebido dos títulos ativos do pré-acerto − valor do acerto), arredondado em duas casas
    public static BigDecimal excedente(BigDecimal recebidoDoPreAcerto, BigDecimal valorDoAcerto) {
        // valor do acerto negativo (desconto acima do total) conta como zero: o excedente nunca supera o que foi recebido
        BigDecimal acerto = CalculoFinanceiro.arredondar(valorDoAcerto).max(BigDecimal.ZERO);
        BigDecimal diferenca = CalculoFinanceiro.arredondar(recebidoDoPreAcerto).subtract(acerto);
        return diferenca.signum() > 0 ? diferenca : BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
    }

    // Opção B (decisão do usuário): na confirmação do acerto, se o título ativo do pré-acerto (T) está acima do acerto (A), o sistema propõe reduzir
    // o título para max(A, recebido R), com aceite explícito. Se R > A o excedente (R - A) nasce A_DESTINAR.
    public record Ajuste(BigDecimal titulado, BigDecimal recebido, BigDecimal valorAcerto, BigDecimal valorNovo, BigDecimal reducao) {
        public boolean cancelaTitulo() {
            return valorNovo.signum() == 0;
        }
    }

    // null = não há ajuste a propor. Com A = 0 e R = 0 (acerto sem valor a pagar e nada pago) o valor novo é 0: o título do pré-acerto é CANCELADO
    // e, em seguida, o acerto entra na regra do título zerado da 6.3. Com A = 0 e R > 0 o título vai para R e o excedente é R.
    public static Ajuste ajuste(BigDecimal titulado, BigDecimal recebido, BigDecimal valorDoAcerto) {
        BigDecimal t = CalculoFinanceiro.arredondar(titulado);
        BigDecimal r = CalculoFinanceiro.arredondar(recebido);
        BigDecimal a = CalculoFinanceiro.arredondar(valorDoAcerto).max(BigDecimal.ZERO);
        if (t.compareTo(a) <= 0) return null;
        BigDecimal novo = a.max(r);
        // título integralmente recebido (R = T): nada a reduzir; só o excedente (R - A) nasce
        if (novo.compareTo(t) >= 0) return null;
        return new Ajuste(t, r, a, novo, t.subtract(novo));
    }

    // novos valores dos títulos do pré-acerto (mesma ordem da lista) para totalizar o valor novo: a redução consome o saldo em aberto a partir da última parcela;
    // um título sem recebimento totalmente reduzido fica com 0 (será cancelado); com recebimento nunca fica abaixo do recebido
    public static java.util.List<BigDecimal> novosValores(java.util.List<BigDecimal> valores, java.util.List<BigDecimal> recebidos, BigDecimal reducao) {
        BigDecimal restante = CalculoFinanceiro.arredondar(reducao);
        BigDecimal[] resultado = new BigDecimal[valores.size()];
        for (int i = valores.size() - 1; i >= 0; i--) {
            BigDecimal valor = CalculoFinanceiro.arredondar(valores.get(i));
            BigDecimal aberto = valor.subtract(CalculoFinanceiro.arredondar(recebidos.get(i)));
            BigDecimal corte = restante.min(aberto).max(BigDecimal.ZERO);
            resultado[i] = valor.subtract(corte);
            restante = restante.subtract(corte);
        }
        return java.util.Arrays.asList(resultado);
    }

    public static String perguntaDoAjuste(Ajuste a) {
        if (a.cancelaTitulo()) return "O título do pré-acerto será cancelado, pois nada foi pago e o acerto não tem valor a pagar.";
        return "O título do pré-acerto (" + MovimentacaoBancoService.moeda(a.titulado()) + ") está acima do acerto (" + MovimentacaoBancoService.moeda(a.valorAcerto())
                + "). Ajustar o título para " + MovimentacaoBancoService.moeda(a.valorNovo()) + "?";
    }
}
