package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;

import java.math.BigDecimal;
import java.util.Set;

// regras puras da aba Financeiro do cliente (selo da consignação, filtros rápidos e paginação)
public final class ClienteFinanceiroCalculo {

    private ClienteFinanceiroCalculo() {}

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    public enum Selo { CANCELADA, SEM_VALOR_A_PAGAR, SEM_TITULO, QUITADA, PARCIAL_VENCIDA, VENCIDA, PARCIAL, EM_ABERTO }

    public enum Filtro {
        TODAS(null), EM_ABERTO(Set.of(Selo.EM_ABERTO, Selo.PARCIAL, Selo.VENCIDA, Selo.PARCIAL_VENCIDA)),
        VENCIDAS(Set.of(Selo.VENCIDA, Selo.PARCIAL_VENCIDA)), QUITADAS(Set.of(Selo.QUITADA));

        private final Set<Selo> selos;

        Filtro(Set<Selo> selos) {
            this.selos = selos;
        }

        public boolean aceita(Selo selo) {
            return selos == null || selos.contains(selo);
        }
    }

    public static Filtro filtro(String texto) {
        if (texto == null || texto.isBlank()) return Filtro.TODAS;
        try {
            return Filtro.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ItemInvalidoException("Filtro inválido. Use TODAS, EM_ABERTO, VENCIDAS ou QUITADAS.");
        }
    }

    public static int tamanho(int size) {
        if (size < 1) return TAMANHO_PADRAO;
        return Math.min(size, TAMANHO_MAXIMO);
    }

    public static int pagina(int page) {
        return Math.max(page, 0);
    }

    // precedência: cancelada > sem valor a pagar (sem movimento) > sem título > quitada > vencida (parcial ou não) > parcial > em aberto.
    // temDocumento/valorAPagar valem só para consignação; no grupo "sem consignação" passe temDocumento = false.
    public static Selo selo(boolean consignacaoCancelada, int quantidadeTitulos, int quantidadeAtivos, boolean temDocumento, BigDecimal valorAPagar,
                            BigDecimal emAberto, BigDecimal valorPago, boolean temVencido) {
        if (consignacaoCancelada || (quantidadeTitulos > 0 && quantidadeAtivos == 0)) return Selo.CANCELADA;
        // documento sem valor a pagar e sem movimento nos títulos; se houver saldo em aberto ou pagamento, vale a situação real dos títulos
        boolean semMovimento = (emAberto == null || emAberto.signum() == 0) && (valorPago == null || valorPago.signum() == 0);
        if (temDocumento && valorAPagar != null && valorAPagar.signum() == 0 && semMovimento) return Selo.SEM_VALOR_A_PAGAR;
        if (quantidadeAtivos == 0) return Selo.SEM_TITULO;
        if (emAberto == null || emAberto.signum() == 0) return Selo.QUITADA;
        boolean parcial = valorPago != null && valorPago.signum() > 0;
        if (temVencido) return parcial ? Selo.PARCIAL_VENCIDA : Selo.VENCIDA;
        return parcial ? Selo.PARCIAL : Selo.EM_ABERTO;
    }
}
