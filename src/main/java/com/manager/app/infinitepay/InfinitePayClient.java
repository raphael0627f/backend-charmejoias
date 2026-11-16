package com.manager.app.infinitepay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

// chamadas à API de checkout da InfinitePay (POST /links). A URL base vem da propriedade app.infinitepay.base-url (permite servidor simulado).
// A chave só trafega no cabeçalho X-Api-Key: nunca é registrada em log nem aparece em mensagens.
@Slf4j
@Component
public class InfinitePayClient {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public record LinkCriado(String url, String respostaBruta) {
    }

    public InfinitePayClient(@Value("${app.infinitepay.base-url:https://api.checkout.infinitepay.io}") String baseUrl,
                             @Value("${app.infinitepay.timeout-segundos:20}") long timeoutSegundos,
                             ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public LinkCriado criarLink(String chave, Map<String, Object> pedido) {
        String corpo;
        try {
            corpo = client.post()
                    .uri("/links")
                    .header("X-Api-Key", chave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                    .body(pedido)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw traduzir(e);
        } catch (RestClientException e) {
            log.warn("InfinitePay indisponível ao criar link: {}", e.getClass().getSimpleName());
            throw new InfinitePayApiException("A InfinitePay está indisponível no momento. Tente novamente em instantes.", e);
        }
        JsonNode json = null;
        try {
            json = corpo == null ? null : objectMapper.readTree(corpo);
        } catch (RuntimeException e) {
            // resposta que não é JSON: ainda pode ser a própria URL em texto simples
        }
        String url = LinkPagamentoCalculo.extrairUrl(json, corpo);
        String bruta = LinkPagamentoCalculo.truncar(corpo, LinkPagamentoCalculo.RESPOSTA_MAXIMA);
        if (url == null) {
            log.warn("InfinitePay não devolveu URL do link. Resposta (truncada): {}", LinkPagamentoCalculo.truncar(corpo, 300));
            throw new InfinitePayApiException("A InfinitePay não devolveu o link de pagamento.");
        }
        return new LinkCriado(url, bruta);
    }

    // só o status e um trecho curto do corpo vão para o log (o corpo da InfinitePay não contém a chave); o usuário recebe mensagem formal
    private InfinitePayApiException traduzir(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        log.warn("InfinitePay respondeu HTTP {} ao criar link: {}", status, LinkPagamentoCalculo.truncar(e.getResponseBodyAsString(), 300));
        if (status == 401 || status == 403) {
            return new InfinitePayApiException("A InfinitePay não aceitou as credenciais configuradas. Verifique a InfiniteTag e a chave em Sistema.", e);
        }
        if (status >= 500 || status == 429 || status == 408) {
            return new InfinitePayApiException("A InfinitePay está indisponível no momento. Tente novamente em instantes.", e);
        }
        return new InfinitePayApiException("A InfinitePay recusou os dados do link. Revise o valor, a validade e as opções.", e);
    }
}
