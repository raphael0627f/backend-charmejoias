package com.manager.app.infra.brasilapi;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class BrasilApiClient {

    private final RestClient client = RestClient.builder()
            .baseUrl("https://brasilapi.com.br/api")
            .build();

    @SuppressWarnings("unchecked")
    public String buscarCodigoIbgeMunicipio(String cep){
        if (cep == null) return null;
        String cepLimpo = cep.replaceAll("\\D", "");
        if (cepLimpo.length() != 8) return null;

        try {
            Map<String, Object> resposta = client.get()
                    .uri("/cep/v2/{cep}", cepLimpo)
                    .retrieve()
                    .body(Map.class);

            Object ibge = resposta != null ? resposta.get("ibge") : null;
            if (ibge instanceof Map<?, ?> ibgeMap) {
                Object city = ((Map<String, Object>) ibgeMap).get("city");
                return city != null ? city.toString() : null;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
