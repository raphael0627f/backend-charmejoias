package com.manager.app.infinitepay;

import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cliente de SAÍDA do receptor na nuvem (Cloudflare): o ERP busca a fila, confirma (ack) e consulta a saúde. O servidor local nunca é exposto.
// O segredo trafega só no cabeçalho Authorization e nunca é registrado em log nem aparece em mensagens. Chamadas curtas, com timeout.
@Slf4j
@Component
public class ReceptorClient {

    public static final int LIMITE_PADRAO = 50;

    private final InfinitePayAutomaticoService automatico;
    private final ObjectMapper objectMapper;
    private final JdkClientHttpRequestFactory factory;

    // item da fila: o corpo (payload) vem bruto, exatamente como a InfinitePay enviou
    public record ItemFila(long id, String recebidoEm, String evento, String orderNsu, String transactionNsu, String invoiceSlug, String corpo) {
    }

    public ReceptorClient(InfinitePayAutomaticoService automatico, ObjectMapper objectMapper,
                          @Value("${app.infinitepay.receptor-timeout-segundos:10}") long timeoutSegundos) {
        this.automatico = automatico;
        this.objectMapper = objectMapper;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.factory = new JdkClientHttpRequestFactory(http);
        this.factory.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
    }

    public List<ItemFila> buscarFila(int limite) {
        InfinitePayAutomaticoService.Receptor r = automatico.receptor();
        JsonNode json = ler(chamar(r, "GET", "/fila?limite=" + Math.max(1, Math.min(limite, 200)), null, "buscar a fila"), "buscar a fila");
        JsonNode itens = json.isArray() ? json : json.path("itens");
        List<ItemFila> lista = new ArrayList<>();
        if (itens.isArray()) {
            for (JsonNode i : itens) {
                if (!i.hasNonNull("id")) continue;
                lista.add(new ItemFila(i.path("id").asLong(), texto(i, "recebidoEm"), texto(i, "evento"), texto(i, "orderNsu"), texto(i, "transactionNsu"),
                        texto(i, "invoiceSlug"), i.has("corpo") && !i.get("corpo").isNull() ? (i.get("corpo").isString() ? i.get("corpo").asString() : i.get("corpo").toString()) : null));
            }
        }
        return lista;
    }

    // confirma o recebimento dos itens (o receptor os marca e apaga depois da retenção). Idempotente.
    public void ack(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        InfinitePayAutomaticoService.Receptor r = automatico.receptor();
        chamar(r, "POST", "/fila/ack", Map.of("ids", ids), "confirmar a fila");
    }

    public InfinitePayDTOs.ReceptorSaude saude() {
        InfinitePayAutomaticoService.Receptor r = automatico.receptor();
        JsonNode j = ler(chamar(r, "GET", "/saude", null, "consultar o receptor"), "consultar o receptor");
        return new InfinitePayDTOs.ReceptorSaude(j.path("ok").asBoolean(false), j.hasNonNull("pendentes") ? j.get("pendentes").asLong() : null,
                texto(j, "maisAntigoEm"), texto(j, "versao"));
    }

    private static String texto(JsonNode no, String campo) {
        return no.hasNonNull(campo) ? no.get(campo).asString() : null;
    }

    private String chamar(InfinitePayAutomaticoService.Receptor r, String metodo, String caminho, Object corpo, String acao) {
        RestClient client = RestClient.builder().baseUrl(r.url()).requestFactory(factory).build();
        try {
            RestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(metodo)).uri(caminho)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + r.segredoErp()).accept(MediaType.APPLICATION_JSON);
            if (corpo != null) spec = spec.contentType(MediaType.APPLICATION_JSON).body(corpo);
            String resposta = spec.retrieve().body(String.class);
            return resposta == null ? "" : resposta;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Receptor respondeu HTTP {} ao {}", status, acao);
            if (status == 401 || status == 403) {
                throw new InfinitePayApiException("O receptor de pagamentos não aceitou o segredo configurado. Verifique o segredo de acesso em Sistema > InfinitePay.", e);
            }
            throw new InfinitePayApiException("O receptor de pagamentos respondeu com erro ao " + acao + ". Tente novamente em instantes.", e);
        } catch (RestClientException e) {
            log.warn("Receptor indisponível ao {}: {}", acao, e.getClass().getSimpleName());
            throw new InfinitePayApiException("O receptor de pagamentos está indisponível no momento. Tente novamente em instantes.", e);
        }
    }

    private JsonNode ler(String corpo, String acao) {
        try {
            return objectMapper.readTree(corpo == null || corpo.isBlank() ? "{}" : corpo);
        } catch (RuntimeException e) {
            throw new InfinitePayApiException("O receptor de pagamentos devolveu uma resposta inválida ao " + acao + ".", e);
        }
    }
}
