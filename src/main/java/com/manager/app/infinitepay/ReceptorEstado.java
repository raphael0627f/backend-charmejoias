package com.manager.app.infinitepay;

import com.manager.app.infinitepay.dto.InfinitePayDTOs;

// contagem de registros retidos no receptor (Cloudflare). Receptor indisponível ou não configurado: disponivel = false e demais campos nulos (nunca erro).
// Com o Worker antigo (sem as contagens novas), os campos novos vêm nulos e "pendentes" segue valendo.
public record ReceptorEstado(boolean disponivel, Long totalRetidos, Long pendentes, Long confirmadosRetidos, String maisAntigoEm, String maisAntigoRetidoEm,
                             Integer retencaoDias) {

    public static final ReceptorEstado INDISPONIVEL = new ReceptorEstado(false, null, null, null, null, null, null);

    public static ReceptorEstado de(InfinitePayDTOs.ReceptorSaude s) {
        if (s == null || !s.ok()) return INDISPONIVEL;
        return new ReceptorEstado(true, s.total(), s.pendentes(), s.confirmados(), s.maisAntigoEm(), s.maisAntigoRetidoEm(), s.retencaoDias());
    }
}
