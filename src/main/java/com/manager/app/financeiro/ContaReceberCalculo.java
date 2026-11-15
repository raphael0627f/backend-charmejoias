package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// cálculos e regras do Contas a Receber sem acesso a banco de dados (testáveis em JUnit puro).
// Parcelamento, datas, descrição das parcelas e competência reutilizam ContaPagarCalculo (mesmas regras, sem dependência de entidade).
public final class ContaReceberCalculo {

    private ContaReceberCalculo() {}

    public static List<BigDecimal> dividir(BigDecimal total, int quantidade) {
        return ContaPagarCalculo.dividir(total, quantidade);
    }

    public static List<BigDecimal> validarValores(List<BigDecimal> valores, BigDecimal total, int quantidade) {
        return ContaPagarCalculo.validarValores(valores, total, quantidade);
    }

    public static void validarQuantidade(Integer quantidade) {
        ContaPagarCalculo.validarQuantidade(quantidade);
    }

    public static List<LocalDate> datas(IntervaloParcelamento intervalo, LocalDate primeiro, List<LocalDate> personalizadas, int quantidade) {
        return ContaPagarCalculo.datas(intervalo, primeiro, personalizadas, quantidade);
    }

    public static String descricaoParcela(String descricao, int numero, int total) {
        return ContaPagarCalculo.descricaoParcela(descricao, numero, total);
    }

    public static String competenciaPadrao(LocalDate vencimento) {
        return ContaPagarCalculo.competenciaPadrao(vencimento);
    }

    public static String validarCompetencia(String competencia) {
        return ContaPagarCalculo.validarCompetencia(competencia);
    }

    // decisão: o vencimento não pode ser anterior à emissão
    public static void validarVencimento(LocalDate emissao, LocalDate vencimento) {
        if (vencimento == null) throw new ItemInvalidoException("O vencimento é obrigatório.");
        if (emissao != null && vencimento.isBefore(emissao)) {
            throw new ItemInvalidoException("O vencimento não pode ser anterior à data de emissão.");
        }
    }

    // situação mostrada: aberto/parcial com vencimento anterior a hoje vira VENCIDO (calculada, nunca gravada)
    public static SituacaoExibicaoContaReceber situacaoExibicao(SituacaoContaReceber situacao, LocalDate vencimento, LocalDate hoje) {
        if ((situacao == SituacaoContaReceber.ABERTO || situacao == SituacaoContaReceber.RECEBIDO_PARCIAL) && vencimento.isBefore(hoje)) {
            return SituacaoExibicaoContaReceber.VENCIDO;
        }
        return SituacaoExibicaoContaReceber.valueOf(situacao.name());
    }

    // situação gravada a partir do valor e do recebido (não trata CANCELADO)
    public static SituacaoContaReceber situacaoPara(BigDecimal valor, BigDecimal valorRecebido) {
        BigDecimal recebido = CalculoFinanceiro.arredondar(valorRecebido);
        if (recebido.signum() <= 0) return SituacaoContaReceber.ABERTO;
        return recebido.compareTo(CalculoFinanceiro.arredondar(valor)) >= 0 ? SituacaoContaReceber.RECEBIDO : SituacaoContaReceber.RECEBIDO_PARCIAL;
    }

    // valor em aberto: cancelado não tem; nunca negativo
    public static BigDecimal valorAberto(SituacaoContaReceber situacao, BigDecimal valor, BigDecimal valorRecebido) {
        if (situacao == SituacaoContaReceber.CANCELADO) return BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        BigDecimal aberto = CalculoFinanceiro.arredondar(valor).subtract(CalculoFinanceiro.arredondar(valorRecebido));
        return aberto.signum() < 0 ? BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA) : aberto;
    }

    // o valor não pode ficar abaixo do já recebido
    public static void validarValorNaoAbaixoDoRecebido(BigDecimal novoValor, BigDecimal valorRecebido) {
        if (novoValor.compareTo(CalculoFinanceiro.arredondar(valorRecebido)) < 0) {
            throw new ItemInvalidoException("O valor do título não pode ser inferior ao valor já recebido ("
                    + MovimentacaoBancoService.moeda(valorRecebido) + ").");
        }
    }

    // o cliente só pode ser trocado enquanto não há valor recebido
    public static void validarTrocaDeCliente(boolean trocou, BigDecimal valorRecebido) {
        if (trocou && CalculoFinanceiro.arredondar(valorRecebido).signum() > 0) {
            throw new ItemInvalidoException("O cliente não pode ser alterado em título com recebimento. Estorne os recebimentos antes.");
        }
    }

    // título cancelado ou com valor recebido não pode ser cancelado (precisa estornar antes)
    public static void validarCancelavel(SituacaoContaReceber situacao, BigDecimal valorRecebido) {
        if (situacao == SituacaoContaReceber.CANCELADO) throw new ItemInvalidoException("O título já está cancelado.");
        if (CalculoFinanceiro.arredondar(valorRecebido).signum() > 0) {
            throw new ItemInvalidoException("O título possui recebimentos ativos. Estorne os recebimentos antes de cancelar.");
        }
    }

    public static void validarVencimentoAlteravel(SituacaoContaReceber situacao) {
        if (situacao == SituacaoContaReceber.RECEBIDO || situacao == SituacaoContaReceber.CANCELADO) {
            throw new ItemInvalidoException("Não é possível alterar o vencimento de um título recebido ou cancelado.");
        }
    }
}
