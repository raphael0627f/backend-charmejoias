package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// cálculos e regras do recebimento e do estorno de recebimento sem acesso a banco de dados (testáveis em JUnit puro).
// valorTotal = principal + juros + multa - desconto (ENTRA no banco); somente o PRINCIPAL abate o título.
public final class RecebimentoCalculo {

    private RecebimentoCalculo() {}

    public record ResultadoRecebimento(ContaReceberRecebimento recebimento, MovimentacaoBanco movimentacao) {}

    public static BigDecimal valorTotal(BigDecimal principal, BigDecimal juros, BigDecimal multa, BigDecimal desconto) {
        return PagamentoCalculo.valorTotal(principal, juros, multa, desconto);
    }

    // título apto a receber: ABERTO ou RECEBIDO_PARCIAL
    public static void validarTitulo(ContaReceber titulo) {
        if (titulo.getSituacao() == SituacaoContaReceber.CANCELADO) {
            throw new ItemInvalidoException("O título cancelado não admite recebimento.");
        }
        if (titulo.getSituacao() == SituacaoContaReceber.RECEBIDO) {
            throw new ItemInvalidoException("O título já está integralmente recebido.");
        }
    }

    // valida e aplica o recebimento: atualiza valor recebido e situação do título e devolve o recebimento e a movimentação de ENTRADA (ainda não gravados)
    public static ResultadoRecebimento receber(ContaReceber titulo, Banco banco, FormaPagamento forma, LocalDate dataRecebimento, BigDecimal principal,
                                               BigDecimal juros, BigDecimal multa, BigDecimal desconto, String observacao, Usuario usuario, LocalDate hoje) {
        validarTitulo(titulo);
        BigDecimal p = CalculoFinanceiro.validarValorPositivo(principal);
        BigDecimal j = PagamentoCalculo.opcional(juros, "juros");
        BigDecimal m = PagamentoCalculo.opcional(multa, "multa");
        BigDecimal d = PagamentoCalculo.opcional(desconto, "desconto");
        CalculoFinanceiro.validarDataNaoFutura(dataRecebimento, hoje);
        if (banco == null) throw new ItemInvalidoException("Informe o banco.");
        if (forma == null) throw new ItemInvalidoException("Informe a forma de pagamento.");
        CalculoFinanceiro.validarBancoAtivo(banco);
        if (!forma.isAtivo()) throw new ItemInvalidoException("A forma de pagamento \"" + forma.getNome() + "\" está desativada.");

        BigDecimal aberto = ContaReceberCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorRecebido());
        if (p.compareTo(aberto) > 0) {
            throw new ItemInvalidoException("O valor principal não pode ser superior ao valor em aberto (" + MovimentacaoBancoService.moeda(aberto) + ").");
        }
        if (d.compareTo(p.add(j).add(m)) > 0) {
            throw new ItemInvalidoException("O desconto não pode ser superior à soma do valor principal, dos juros e da multa.");
        }
        BigDecimal total = valorTotal(p, j, m, d);
        if (total.signum() <= 0) throw new ItemInvalidoException("O valor total do recebimento deve ser maior que zero.");

        ContaReceberRecebimento r = new ContaReceberRecebimento();
        r.setContaReceber(titulo);
        r.setDataRecebimento(dataRecebimento);
        r.setValorPrincipal(p);
        r.setJuros(j);
        r.setMulta(m);
        r.setDesconto(d);
        r.setValorTotal(total);
        r.setBanco(banco);
        r.setFormaPagamento(forma);
        r.setObservacao(observacao == null || observacao.isBlank() ? null : observacao.trim());

        titulo.setValorRecebido(CalculoFinanceiro.arredondar(titulo.getValorRecebido()).add(p));
        titulo.setSituacao(ContaReceberCalculo.situacaoPara(titulo.getValor(), titulo.getValorRecebido()));

        MovimentacaoBanco entrada = CalculoFinanceiro.movimentacao(banco, dataRecebimento, TipoMovimentacao.ENTRADA, total,
                OrigemMovimentacao.RECEBIMENTO_CONTA_RECEBER,
                "Recebimento do título nº " + titulo.getNumero() + " - " + titulo.getDescricao() + " - " + ContaReceberMapper.pagadorExibicao(titulo), usuario);
        entrada.setContaReceberId(titulo.getId());
        return new ResultadoRecebimento(r, entrada);
    }

    // saldo integral em aberto (recebimento em lote); zero = nada a receber
    public static BigDecimal saldoIntegral(ContaReceber titulo) {
        validarTitulo(titulo);
        BigDecimal aberto = ContaReceberCalculo.valorAberto(titulo.getSituacao(), titulo.getValor(), titulo.getValorRecebido());
        if (aberto.signum() <= 0) throw new ItemInvalidoException("O título não possui valor em aberto.");
        return aberto;
    }

    // estorno: marca o recebimento e a movimentação original, reduz o valor recebido pelo principal e devolve a movimentação inversa (SAÍDA).
    // Permitido com banco desativado e mesmo que o saldo do banco fique negativo (a resposta traz o aviso).
    public static MovimentacaoBanco estornar(ContaReceberRecebimento recebimento, MovimentacaoBanco original, String motivo, Usuario usuario,
                                             LocalDate hoje, LocalDateTime agora) {
        if (recebimento.isEstornada()) throw new ItemInvalidoException("Este recebimento já foi estornado.");
        ContaReceber titulo = recebimento.getContaReceber();
        recebimento.setEstornada(true);
        recebimento.setEstornoMotivo(motivo);
        recebimento.setEstornadaEm(agora);
        recebimento.setEstornadaPor(usuario);

        BigDecimal recebido = CalculoFinanceiro.arredondar(titulo.getValorRecebido()).subtract(CalculoFinanceiro.arredondar(recebimento.getValorPrincipal()));
        titulo.setValorRecebido(recebido.signum() < 0 ? BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA) : recebido);
        titulo.setSituacao(ContaReceberCalculo.situacaoPara(titulo.getValor(), titulo.getValorRecebido()));

        original.setEstornada(true);
        original.setMotivoEstorno(motivo);
        original.setEstornadaEm(agora);
        original.setEstornadaPor(usuario);
        MovimentacaoBanco inversa = CalculoFinanceiro.movimentacao(original.getBanco(), hoje, TipoMovimentacao.SAIDA, original.getValor(),
                OrigemMovimentacao.ESTORNO_RECEBIMENTO, "Estorno do recebimento do título nº " + titulo.getNumero() + " - " + titulo.getDescricao() + ": " + motivo, usuario);
        inversa.setEstornoDe(original);
        inversa.setContaReceberId(titulo.getId());
        inversa.setRecebimentoId(recebimento.getId());
        return inversa;
    }
}
