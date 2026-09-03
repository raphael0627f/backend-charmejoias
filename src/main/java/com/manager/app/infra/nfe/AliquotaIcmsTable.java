package com.manager.app.infra.nfe;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public class AliquotaIcmsTable {

    // alíquota interna por UF - confirmar com contabilidade antes de usar em produção
    private static final Map<String, BigDecimal> ALIQUOTA_INTERNA = Map.ofEntries(
            Map.entry("AC", new BigDecimal("19")),
            Map.entry("AL", new BigDecimal("19")),
            Map.entry("AP", new BigDecimal("18")),
            Map.entry("AM", new BigDecimal("20")),
            Map.entry("BA", new BigDecimal("19")),
            Map.entry("CE", new BigDecimal("20")),
            Map.entry("DF", new BigDecimal("20")),
            Map.entry("ES", new BigDecimal("17")),
            Map.entry("GO", new BigDecimal("19")),
            Map.entry("MA", new BigDecimal("22")),
            Map.entry("MT", new BigDecimal("17")),
            Map.entry("MS", new BigDecimal("17")),
            Map.entry("MG", new BigDecimal("18")),
            Map.entry("PA", new BigDecimal("19")),
            Map.entry("PB", new BigDecimal("20")),
            Map.entry("PR", new BigDecimal("19.5")),
            Map.entry("PE", new BigDecimal("20.5")),
            Map.entry("PI", new BigDecimal("21")),
            Map.entry("RJ", new BigDecimal("20")),
            Map.entry("RN", new BigDecimal("18")),
            Map.entry("RS", new BigDecimal("17")),
            Map.entry("RO", new BigDecimal("19.5")),
            Map.entry("RR", new BigDecimal("20")),
            Map.entry("SC", new BigDecimal("17")),
            Map.entry("SP", new BigDecimal("18")),
            Map.entry("SE", new BigDecimal("19")),
            Map.entry("TO", new BigDecimal("20"))
    );

    private static final Set<String> ORIGEM_SUL_SUDESTE = Set.of("RS", "SC", "PR", "SP", "RJ", "MG");

    private AliquotaIcmsTable() {
    }

    public static BigDecimal aliquotaInterna(String uf) {
        BigDecimal aliquota = ALIQUOTA_INTERNA.get(uf == null ? null : uf.toUpperCase());
        if (aliquota == null) {
            throw new IllegalArgumentException("UF sem alíquota de ICMS cadastrada: " + uf);
        }
        return aliquota;
    }

    public static BigDecimal aliquotaInterestadual(String ufOrigem, String ufDestino) {
        boolean origemSulSudeste = ORIGEM_SUL_SUDESTE.contains(ufOrigem == null ? null : ufOrigem.toUpperCase());
        boolean destinoSulSudeste = ORIGEM_SUL_SUDESTE.contains(ufDestino == null ? null : ufDestino.toUpperCase());

        if (origemSulSudeste && !destinoSulSudeste) {
            return new BigDecimal("7");
        }
        return new BigDecimal("12");
    }
}
