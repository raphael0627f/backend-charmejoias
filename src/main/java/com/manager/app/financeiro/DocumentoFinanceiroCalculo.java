package com.manager.app.financeiro;

import java.math.BigDecimal;

// valor, saldo e regras de geração de título a receber a partir de pré-acerto e acerto de consignação, sem acesso a banco (testáveis em JUnit puro)
public final class DocumentoFinanceiroCalculo {

    private DocumentoFinanceiroCalculo() {}

    public enum Tipo {
        PRE_ACERTO("pré-acerto", "O pré-acerto"), ACERTO("acerto", "O acerto");

        private final String nome;
        private final String nomeComArtigo;

        Tipo(String nome, String nomeComArtigo) {
            this.nome = nome;
            this.nomeComArtigo = nomeComArtigo;
        }

        public String nome() {
            return nome;
        }

        public String nomeComArtigo() {
            return nomeComArtigo;
        }
    }

    public record Saldo(BigDecimal saldoATitular, BigDecimal excedente) {
    }

    // valor do pré-acerto: soma de quantidade x preço menos o desconto (valor absoluto, nulo = zero)
    public static BigDecimal valorPreAcerto(BigDecimal somaItens, BigDecimal desconto) {
        BigDecimal soma = somaItens == null ? BigDecimal.ZERO : somaItens;
        BigDecimal abatimento = desconto == null ? BigDecimal.ZERO : desconto;
        return CalculoFinanceiro.arredondar(soma.subtract(abatimento));
    }

    // saldo a titular nunca é negativo; o que excede o valor do documento aparece como excedente
    public static Saldo saldo(BigDecimal valorDocumento, BigDecimal titulado) {
        BigDecimal valor = CalculoFinanceiro.arredondar(valorDocumento);
        BigDecimal ativo = CalculoFinanceiro.arredondar(titulado);
        BigDecimal diferenca = valor.subtract(ativo);
        BigDecimal zero = BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
        return new Saldo(diferenca.signum() > 0 ? diferenca : zero, diferenca.signum() < 0 ? diferenca.negate() : zero);
    }

    // excedente do que foi efetivamente recebido sobre o valor do documento
    public static BigDecimal excedenteRecebido(BigDecimal valorDocumento, BigDecimal recebido) {
        BigDecimal diferenca = CalculoFinanceiro.arredondar(recebido).subtract(CalculoFinanceiro.arredondar(valorDocumento));
        return diferenca.signum() > 0 ? diferenca : BigDecimal.ZERO.setScale(CalculoFinanceiro.ESCALA);
    }

    public record Situacao(Tipo tipo, boolean temConsignacao, boolean acertoConfirmado, boolean clienteAtivo, String nomeCliente,
                           BigDecimal valorDocumento, boolean tituloProprioAtivo, boolean tituloDoAcertoAtivo, Saldo saldo) {
    }

    // motivo formal que impede gerar o título, ou null quando pode gerar. A ordem das regras é a mesma do GET e do POST.
    public static String motivoBloqueio(Situacao s) {
        Tipo t = s.tipo();
        // o pré-acerto pode não ter consignação de referência (título gerado normalmente); o acerto sempre tem
        if (t == Tipo.ACERTO && !s.temConsignacao()) return "O acerto não possui consignação e não gera título a receber.";
        if (t == Tipo.ACERTO && !s.acertoConfirmado()) return "O acerto precisa estar confirmado para gerar título a receber.";
        // o título próprio ativo vem antes do valor: o acerto sem valor a pagar já tem o título zerado automático
        if (s.tituloProprioAtivo()) return t.nomeComArtigo() + " já possui título a receber ativo.";
        if (s.valorDocumento().signum() <= 0) return t.nomeComArtigo() + " deve ter valor maior que zero para gerar título a receber.";
        if (t == Tipo.PRE_ACERTO && s.tituloDoAcertoAtivo()) return "A consignação já possui título a receber do acerto.";
        if (s.saldo().saldoATitular().signum() <= 0) {
            if (s.saldo().excedente().signum() > 0) {
                return t.nomeComArtigo() + " já está titulado acima do seu valor (excedente de " + MovimentacaoBancoService.moeda(s.saldo().excedente()) + ").";
            }
            return t.nomeComArtigo() + " já está totalmente titulado.";
        }
        if (!s.clienteAtivo()) return "O cliente \"" + s.nomeCliente() + "\" está desativado.";
        return null;
    }

    public static final String OBSERVACAO_TITULO_ZERADO = "Acerto sem valor a pagar. Título registrado automaticamente, sem cobrança.";

    // o acerto sem valor a pagar gera o título zerado somente com o valor exatamente 0,00 e sem nenhum título ativo de pré-acerto ou do próprio acerto
    public static boolean geraTituloZerado(BigDecimal valorAcerto, boolean temTituloDePreAcertoOuDoAcerto) {
        return CalculoFinanceiro.arredondar(valorAcerto).signum() == 0 && !temTituloDePreAcertoOuDoAcerto;
    }

    // título de acerto com valor zero: registro automático, não editável nem cancelável
    public static boolean tituloZerado(BigDecimal valor) {
        return valor != null && CalculoFinanceiro.arredondar(valor).signum() == 0;
    }

    // aviso formal quando o título ativo supera o valor do documento (crédito de cliente fica para fase posterior)
    public static String aviso(Tipo tipo, Saldo saldo) {
        if (saldo.excedente().signum() <= 0) return null;
        return "Atenção: o valor titulado supera o valor d" + (tipo == Tipo.ACERTO ? "o acerto" : "o pré-acerto") + " em "
                + MovimentacaoBancoService.moeda(saldo.excedente()) + ". Nenhum novo título será gerado; o tratamento do excedente (crédito do cliente) não está disponível nesta versão.";
    }

    // descrição automática do título, em maiúsculas e curta
    public static String descricaoAutomatica(Tipo tipo, Long consignacaoId, Long documentoId) {
        String documento = (tipo == Tipo.ACERTO ? "ACERTO Nº " : "PRÉ-ACERTO Nº ") + documentoId;
        return consignacaoId == null ? documento : "CONS. Nº " + consignacaoId + " - " + documento;
    }
}
