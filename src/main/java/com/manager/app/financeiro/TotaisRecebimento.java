package com.manager.app.financeiro;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// somas dos recebimentos NÃO estornados de um título (regra do "valor realmente pago": total = principal + juros + multa - desconto; crédito do cliente conta;
// a taxa paga pelo cliente na InfinitePay fica à parte, fora do total)
public record TotaisRecebimento(Long tituloId, BigDecimal total, BigDecimal principal, BigDecimal juros, BigDecimal multa, BigDecimal desconto,
                                BigDecimal taxaCliente, BigDecimal credito) {

    public static final TotaisRecebimento ZERO = new TotaisRecebimento(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public static TotaisRecebimento de(Map<Long, TotaisRecebimento> mapa, Long tituloId) {
        TotaisRecebimento t = mapa == null ? null : mapa.get(tituloId);
        return t == null ? ZERO : t;
    }

    // a partir dos recebimentos já carregados (detalhe do título)
    public static TotaisRecebimento de(Long tituloId, List<ContaReceberRecebimento> recebimentos) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal juros = BigDecimal.ZERO;
        BigDecimal multa = BigDecimal.ZERO;
        BigDecimal desconto = BigDecimal.ZERO;
        BigDecimal taxa = BigDecimal.ZERO;
        BigDecimal credito = BigDecimal.ZERO;
        for (ContaReceberRecebimento r : recebimentos) {
            if (r.isEstornada()) continue;
            total = total.add(nz(r.getValorTotal()));
            principal = principal.add(nz(r.getValorPrincipal()));
            juros = juros.add(nz(r.getJuros()));
            multa = multa.add(nz(r.getMulta()));
            desconto = desconto.add(nz(r.getDesconto()));
            taxa = taxa.add(nz(r.getValorTaxaCliente()));
            if (r.isCredito()) credito = credito.add(nz(r.getValorTotal()));
        }
        return new TotaisRecebimento(tituloId, total, principal, juros, multa, desconto, taxa, credito);
    }

    public boolean creditoAplicado() {
        return credito != null && credito.signum() > 0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
