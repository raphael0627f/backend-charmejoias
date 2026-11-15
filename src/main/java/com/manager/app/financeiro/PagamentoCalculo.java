package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// cálculos e regras do pagamento e do estorno de pagamento sem acesso a banco de dados (testáveis em JUnit puro).
// valorTotal = principal + juros + multa - desconto (sai do banco); somente o PRINCIPAL abate o título.
public final class PagamentoCalculo {

    private PagamentoCalculo() {}

    public record ResultadoPagamento(ContaPagarPagamento pagamento, MovimentacaoBanco movimentacao) {}

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

    public static final String MSG_LOTE_VARIAVEL = "Título de valor variável: confirme o valor da conta no pagamento individual.";
    public static final String MSG_CONFIRMACAO_INDEVIDA = "Os dados da conta só podem ser confirmados em título de valor variável ainda não confirmado.";
    public static final String MSG_VALOR_CONTA_OBRIGATORIO = "Informe o valor da conta deste mês para registrar o pagamento.";

    // valores e datas da confirmação, para o histórico
    public record Confirmacao(BigDecimal valorAntes, BigDecimal valorDepois, LocalDate vencimentoAntes, LocalDate vencimentoDepois) {
        public String descricao() {
            return "Dados da conta confirmados: valor de " + MovimentacaoBancoService.moeda(valorAntes) + " para " + MovimentacaoBancoService.moeda(valorDepois)
                    + "; vencimento de " + com.manager.app.shared.TextoUtils.data(vencimentoAntes) + " para " + com.manager.app.shared.TextoUtils.data(vencimentoDepois) + ".";
        }
    }

    // confirmação de valor e vencimento ao pagar. Devolve nulo quando não há o que confirmar (título comum sem os campos).
    // Só vale para título estimado e sem pagamento ativo; em título estimado o valor da conta é obrigatório.
    public static Confirmacao confirmar(ContaPagar titulo, BigDecimal valorConta, LocalDate vencimento, boolean comPagamentoAtivo) {
        boolean informado = valorConta != null || vencimento != null;
        if (!titulo.isEstimado()) {
            if (informado) throw new ItemInvalidoException(MSG_CONFIRMACAO_INDEVIDA);
            return null;
        }
        validarTitulo(titulo);
        if (comPagamentoAtivo) {
            if (informado) throw new ItemInvalidoException(MSG_CONFIRMACAO_INDEVIDA);
            return null;
        }
        if (valorConta == null) throw new ItemInvalidoException(MSG_VALOR_CONTA_OBRIGATORIO);
        return aplicarConfirmacao(titulo, valorConta, vencimento);
    }

    // grava valor e vencimento confirmados no título (vencimento nulo mantém o previsto), recalcula a situação e zera o indicador de estimativa
    public static Confirmacao aplicarConfirmacao(ContaPagar titulo, BigDecimal valor, LocalDate vencimento) {
        BigDecimal v = CalculoFinanceiro.validarValorPositivo(valor);
        Confirmacao c = new Confirmacao(titulo.getValor(), v, titulo.getVencimento(), vencimento == null ? titulo.getVencimento() : vencimento);
        titulo.setValor(v);
        titulo.setVencimento(c.vencimentoDepois());
        titulo.setEstimado(false);
        titulo.setSituacao(ContaPagarCalculo.situacaoPara(titulo.getValor(), titulo.getValorPago()));
        return c;
    }

    // título apto a receber pagamento: ABERTO ou PAGO_PARCIAL
    public static void validarTitulo(ContaPagar titulo) {
        if (titulo.getSituacao() == SituacaoContaPagar.CANCELADO) {
            throw new ItemInvalidoException("O título cancelado não pode receber pagamento.");
        }
        if (titulo.getSituacao() == SituacaoContaPagar.PAGO) {
            throw new ItemInvalidoException("O título já está integralmente pago.");
        }
    }

    // valida e aplica o pagamento: atualiza valor pago e situação do título e devolve o pagamento e a movimentação de SAÍDA (ainda não gravadas)
    public static ResultadoPagamento pagar(ContaPagar titulo, Banco banco, FormaPagamento forma, LocalDate dataPagamento, BigDecimal principal,
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
        if (total.signum() <= 0) throw new ItemInvalidoException("O valor total do pagamento deve ser maior que zero.");

        ContaPagarPagamento pagamento = new ContaPagarPagamento();
        pagamento.setContaPagar(titulo);
        pagamento.setDataPagamento(dataPagamento);
        pagamento.setValorPrincipal(p);
        pagamento.setJuros(j);
        pagamento.setMulta(m);
        pagamento.setDesconto(d);
        pagamento.setValorTotal(total);
        pagamento.setBanco(banco);
        pagamento.setFormaPagamento(forma);
        pagamento.setObservacao(observacao == null || observacao.isBlank() ? null : observacao.trim());

        titulo.setValorPago(CalculoFinanceiro.arredondar(titulo.getValorPago()).add(p));
        titulo.setSituacao(ContaPagarCalculo.situacaoPara(titulo.getValor(), titulo.getValorPago()));

        MovimentacaoBanco saida = CalculoFinanceiro.movimentacao(banco, dataPagamento, TipoMovimentacao.SAIDA, total,
                OrigemMovimentacao.PAGAMENTO_CONTA_PAGAR, "Pagamento do título nº " + titulo.getNumero() + " - " + titulo.getDescricao(), usuario);
        return new ResultadoPagamento(pagamento, saida);
    }

    // saldo integral em aberto do título (pagamento em lote); zero = nada a pagar
    public static BigDecimal saldoIntegral(ContaPagar titulo) {
        validarTitulo(titulo);
        if (titulo.isEstimado()) throw new ItemInvalidoException(MSG_LOTE_VARIAVEL);
        BigDecimal aberto = ContaPagarCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorPago());
        if (aberto.signum() <= 0) throw new ItemInvalidoException("O título não possui valor em aberto.");
        return aberto;
    }

    // estorno: marca o pagamento e a movimentação original, reduz o valor pago do título pelo principal e devolve a movimentação inversa (ENTRADA).
    // Permitido com banco inativo (corrige o histórico).
    public static MovimentacaoBanco estornar(ContaPagarPagamento pagamento, MovimentacaoBanco original, String motivo, Usuario usuario,
                                             LocalDate hoje, LocalDateTime agora) {
        if (pagamento.isEstornada()) throw new ItemInvalidoException("Este pagamento já foi estornado.");
        ContaPagar titulo = pagamento.getContaPagar();
        pagamento.setEstornada(true);
        pagamento.setEstornoMotivo(motivo);
        pagamento.setEstornadaEm(agora);
        pagamento.setEstornadaPor(usuario);

        BigDecimal pago = CalculoFinanceiro.arredondar(titulo.getValorPago()).subtract(CalculoFinanceiro.arredondar(pagamento.getValorPrincipal()));
        titulo.setValorPago(pago.signum() < 0 ? BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA) : pago);
        titulo.setSituacao(ContaPagarCalculo.situacaoPara(titulo.getValor(), titulo.getValorPago()));

        original.setEstornada(true);
        original.setMotivoEstorno(motivo);
        original.setEstornadaEm(agora);
        original.setEstornadaPor(usuario);
        MovimentacaoBanco inversa = CalculoFinanceiro.movimentacao(original.getBanco(), hoje, TipoMovimentacao.ENTRADA, original.getValor(),
                OrigemMovimentacao.ESTORNO_PAGAMENTO, "Estorno do pagamento do título nº " + titulo.getNumero() + " - " + titulo.getDescricao() + ": " + motivo, usuario);
        inversa.setEstornoDe(original);
        inversa.setPagamentoId(pagamento.getId());
        return inversa;
    }
}
