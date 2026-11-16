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

    // o pagador (cliente ou avulso) só pode ser trocado ou alterado enquanto não há valor recebido
    public static void validarTrocaDeCliente(boolean trocou, BigDecimal valorRecebido) {
        if (trocou && CalculoFinanceiro.arredondar(valorRecebido).signum() > 0) {
            throw new ItemInvalidoException("O pagador não pode ser alterado em título com recebimento. Estorne os recebimentos antes.");
        }
    }

    // título gerado a partir de consignação: valor e pagador não mudam (cancelar e gerar de novo), para não divergir do documento de origem
    public static void validarEdicaoDeTituloDeConsignacao(boolean tituloDeConsignacao, boolean alterouValorOuPagador) {
        if (tituloDeConsignacao && alterouValorOuPagador) {
            throw new ItemInvalidoException("O valor e o cliente de título gerado a partir de consignação não podem ser alterados. Cancele o título e gere novamente.");
        }
    }

    // pagador avulso já validado: nome em maiúsculas e documento só com dígitos (ou null)
    public record PagadorAvulso(String nome, String documento) {
    }

    // regra do pagador: informa-se o cliente OU o pagador avulso. Retorna null quando há cliente.
    public static PagadorAvulso validarPagador(boolean temCliente, String nome, String documento) {
        String nomeLimpo = nome == null ? "" : nome.trim();
        String documentoLimpo = documento == null || documento.isBlank() ? null : documento.trim();
        if (temCliente) {
            if (!nomeLimpo.isEmpty() || documentoLimpo != null) {
                throw new ItemInvalidoException("Informe apenas o cliente ou o pagador avulso.");
            }
            return null;
        }
        if (nomeLimpo.isEmpty()) throw new ItemInvalidoException("Informe o cliente ou o pagador avulso.");
        if (nomeLimpo.length() < 2 || nomeLimpo.length() > 150) {
            throw new ItemInvalidoException("O nome do pagador avulso deve ter entre 2 e 150 caracteres.");
        }
        if (nomeLimpo.chars().anyMatch(Character::isISOControl)) {
            throw new ItemInvalidoException("O nome do pagador avulso contém caracteres inválidos.");
        }
        return new PagadorAvulso(nomeLimpo.toUpperCase(java.util.Locale.forLanguageTag("pt-BR")), documentoDoPagador(documentoLimpo));
    }

    // documento opcional: aceita máscara (ponto, traço, barra e espaço); grava só dígitos; 11 (CPF) ou 14 (CNPJ) dígitos
    static String documentoDoPagador(String documento) {
        if (documento == null) return null;
        StringBuilder digitos = new StringBuilder();
        for (char ch : documento.toCharArray()) {
            if (ch >= '0' && ch <= '9') digitos.append(ch);
            else if (ch != '.' && ch != '-' && ch != '/' && ch != ' ') throw mensagemDocumentoPagador();
        }
        if (digitos.length() != 11 && digitos.length() != 14) throw mensagemDocumentoPagador();
        return digitos.toString();
    }

    private static ItemInvalidoException mensagemDocumentoPagador() {
        return new ItemInvalidoException("O documento do pagador avulso deve ter 11 dígitos (CPF) ou 14 dígitos (CNPJ).");
    }

    // nome de exibição: cliente ou pagador avulso
    public static String pagadorExibicao(String nomeCliente, String pagadorNome) {
        return nomeCliente != null ? nomeCliente : pagadorNome;
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
