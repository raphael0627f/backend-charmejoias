package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// cálculos e regras da baixa e do estorno de baixa sem acesso a banco de dados (testáveis em JUnit puro).
// valorTotal = principal + juros + multa - desconto (sai do banco); somente o PRINCIPAL abate o título.
public final class BaixaCalculo {

    private BaixaCalculo() {}

    public record ResultadoBaixa(ContaPagarBaixa baixa, MovimentacaoBanco movimentacao) {}

    public static BigDecimal valorTotal(BigDecimal principal, BigDecimal juros, BigDecimal multa, BigDecimal desconto) {
        return CalculoFinanceiro.arredondar(principal).add(CalculoFinanceiro.arredondar(juros)).add(CalculoFinanceiro.arredondar(multa))
                .subtract(CalculoFinanceiro.arredondar(desconto));
    }

    // juros, multa e desconto são opcionais (nulo = zero), não negativos e com até 2 casas
    static BigDecimal opcional(BigDecimal valor, String nome) {
        if (valor == null) return BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        if (valor.signum() < 0) throw new ItemInvalidoException("O valor de " + nome + " não pode ser negativo.");
        if (valor.stripTrailingZeros().scale() > CalculoFinanceiro.ESCALA) {
            throw new ItemInvalidoException("O valor de " + nome + " deve ter no máximo duas casas decimais.");
        }
        CalculoFinanceiro.validarTeto(valor, nome);
        return CalculoFinanceiro.arredondar(valor);
    }

    // título apto a receber baixa: ABERTO ou PAGO_PARCIAL
    public static void validarTitulo(ContaPagar titulo) {
        if (titulo.getSituacao() == SituacaoContaPagar.CANCELADO) {
            throw new ItemInvalidoException("O título cancelado não pode receber baixa.");
        }
        if (titulo.getSituacao() == SituacaoContaPagar.PAGO) {
            throw new ItemInvalidoException("O título já está integralmente pago.");
        }
    }

    // valida e aplica a baixa: atualiza valor pago e situação do título e devolve a baixa e a movimentação de SAÍDA (ainda não gravadas)
    public static ResultadoBaixa baixar(ContaPagar titulo, Banco banco, FormaPagamento forma, LocalDate dataPagamento, BigDecimal principal,
                                        BigDecimal juros, BigDecimal multa, BigDecimal desconto, String observacao, Usuario usuario, LocalDate hoje) {
        validarTitulo(titulo);
        BigDecimal p = CalculoFinanceiro.validarValorPositivo(principal);
        BigDecimal j = opcional(juros, "juros");
        BigDecimal m = opcional(multa, "multa");
        BigDecimal d = opcional(desconto, "desconto");
        CalculoFinanceiro.validarDataNaoFutura(dataPagamento, hoje);
        if (banco == null) throw new ItemInvalidoException("Informe o banco.");
        if (forma == null) throw new ItemInvalidoException("Informe a forma de pagamento.");
        CalculoFinanceiro.validarBancoAtivo(banco);
        if (!forma.isAtivo()) throw new ItemInvalidoException("A forma de pagamento \"" + forma.getNome() + "\" está inativa.");

        BigDecimal aberto = ContaPagarCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorPago());
        if (p.compareTo(aberto) > 0) {
            throw new ItemInvalidoException("O valor principal não pode ser superior ao valor em aberto (" + MovimentacaoBancoService.moeda(aberto) + ").");
        }
        if (d.compareTo(p.add(j).add(m)) > 0) {
            throw new ItemInvalidoException("O desconto não pode ser superior à soma do valor principal, dos juros e da multa.");
        }
        BigDecimal total = valorTotal(p, j, m, d);
        if (total.signum() <= 0) throw new ItemInvalidoException("O valor total da baixa deve ser maior que zero.");

        ContaPagarBaixa baixa = new ContaPagarBaixa();
        baixa.setContaPagar(titulo);
        baixa.setDataPagamento(dataPagamento);
        baixa.setValorPrincipal(p);
        baixa.setJuros(j);
        baixa.setMulta(m);
        baixa.setDesconto(d);
        baixa.setValorTotal(total);
        baixa.setBanco(banco);
        baixa.setFormaPagamento(forma);
        baixa.setObservacao(observacao == null || observacao.isBlank() ? null : observacao.trim());

        titulo.setValorPago(CalculoFinanceiro.arredondar(titulo.getValorPago()).add(p));
        titulo.setSituacao(ContaPagarCalculo.situacaoPara(titulo.getValor(), titulo.getValorPago()));

        MovimentacaoBanco saida = CalculoFinanceiro.movimentacao(banco, dataPagamento, TipoMovimentacao.SAIDA, total,
                OrigemMovimentacao.BAIXA_CONTA_PAGAR, "Pagamento do título nº " + titulo.getNumero() + " - " + titulo.getDescricao(), usuario);
        return new ResultadoBaixa(baixa, saida);
    }

    // saldo integral em aberto do título (baixa em lote); zero = nada a pagar
    public static BigDecimal saldoIntegral(ContaPagar titulo) {
        validarTitulo(titulo);
        BigDecimal aberto = ContaPagarCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorPago());
        if (aberto.signum() <= 0) throw new ItemInvalidoException("O título não possui valor em aberto.");
        return aberto;
    }

    // estorno: marca a baixa e a movimentação original, reduz o valor pago do título pelo principal e devolve a movimentação inversa (ENTRADA).
    // Permitido com banco inativo (corrige o histórico).
    public static MovimentacaoBanco estornar(ContaPagarBaixa baixa, MovimentacaoBanco original, String motivo, Usuario usuario,
                                             LocalDate hoje, LocalDateTime agora) {
        if (baixa.isEstornada()) throw new ItemInvalidoException("Esta baixa já foi estornada.");
        ContaPagar titulo = baixa.getContaPagar();
        baixa.setEstornada(true);
        baixa.setEstornoMotivo(motivo);
        baixa.setEstornadaEm(agora);
        baixa.setEstornadaPor(usuario);

        BigDecimal pago = CalculoFinanceiro.arredondar(titulo.getValorPago()).subtract(CalculoFinanceiro.arredondar(baixa.getValorPrincipal()));
        titulo.setValorPago(pago.signum() < 0 ? BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA) : pago);
        titulo.setSituacao(ContaPagarCalculo.situacaoPara(titulo.getValor(), titulo.getValorPago()));

        original.setEstornada(true);
        original.setMotivoEstorno(motivo);
        original.setEstornadaEm(agora);
        original.setEstornadaPor(usuario);
        MovimentacaoBanco inversa = CalculoFinanceiro.movimentacao(original.getBanco(), hoje, TipoMovimentacao.ENTRADA, original.getValor(),
                OrigemMovimentacao.ESTORNO_BAIXA, "Estorno da baixa do título nº " + titulo.getNumero() + " - " + titulo.getDescricao() + ": " + motivo, usuario);
        inversa.setEstornoDe(original);
        inversa.setBaixaId(baixa.getId());
        return inversa;
    }
}
