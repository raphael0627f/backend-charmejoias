package com.manager.app.infinitepay;

// códigos estáveis do motivo de revisão (coluna motivo_codigo), com o rótulo exibido ao usuário e as ações que a tela pode oferecer
public enum MotivoRevisao {
    PAYLOAD_INESPERADO("Dados inesperados", false, false),
    EVENTO_INFORMATIVO("Aviso da InfinitePay", false, false),
    LINK_DESCONHECIDO("Link não encontrado", false, true),
    LINK_MANUAL("Link de baixa manual", false, true),
    TITULO_CANCELADO("Pago após cancelamento do título", false, true),
    DUPLICIDADE("Pagamento em duplicidade", false, true),
    VALOR_DIVERGENTE("Valor diverge do link", false, true),
    VALOR_MENOR("Valor pago menor que o cobrado", true, true),
    SALDO_INSUFICIENTE("Saldo do título menor que o pago", false, true),
    FORMA_NAO_MAPEADA("Meio de pagamento sem forma configurada", true, true),
    BANCO_DESATIVADO("Banco do link desativado", true, true),
    CONFIRMACAO_FALHOU("Não foi possível confirmar na InfinitePay (10 tentativas)", false, true),
    ERRO_AO_BAIXAR("Erro ao registrar a baixa", true, true);

    private final String rotulo;
    private final boolean aplicavel;
    private final boolean reprocessavel;

    MotivoRevisao(String rotulo, boolean aplicavel, boolean reprocessavel) {
        this.rotulo = rotulo;
        this.aplicavel = aplicavel;
        this.reprocessavel = reprocessavel;
    }

    public String rotulo() {
        return rotulo;
    }

    // "Aplicar no título" só existe para estes motivos
    public boolean aplicavel() {
        return aplicavel;
    }

    public boolean reprocessavel() {
        return reprocessavel;
    }

    // a tela precisa pedir banco e forma
    public boolean exigeBancoForma() {
        return this == FORMA_NAO_MAPEADA || this == BANCO_DESATIVADO;
    }

    public static MotivoRevisao de(String codigo) {
        if (codigo == null) return null;
        try {
            return valueOf(codigo);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
