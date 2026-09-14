package com.manager.app.spc.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SpcClient {

    private final SpcProperties properties;

    // SOAP nao usa status HTTP pra erro de negocio - muitos servidores (inclusive o do SPC)
    // devolvem Fault com HTTP 500, entao o corpo precisa ser inspecionado nos dois casos
    private static final Pattern FAULT_STRING = Pattern.compile("<faultstring[^>]*>(.*?)</faultstring>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern FAULT_TAG = Pattern.compile("[:<]Fault[ >]", Pattern.CASE_INSENSITIVE);

    private boolean producao(){
        return "producao".equals(properties.getDefaultEnvironment());
    }

    private String autorizacao(){
        String credenciais = properties.getOperador() + ":" + properties.getSenha();
        return "Basic " + Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));
    }

    private RestClient client(String url){
        return RestClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, autorizacao())
                .build();
    }

    public String consultar(String xmlRequest){
        String url = producao() ? properties.getConsultaUrlProducao() : properties.getConsultaUrlHomologacao();
        return enviar(url, xmlRequest);
    }

    public String incluir(String xmlRequest){
        String url = producao() ? properties.getInsumoUrlProducao() : properties.getInsumoUrlHomologacao();
        return enviar(url, xmlRequest);
    }

    public String excluir(String xmlRequest){
        String url = producao() ? properties.getInsumoUrlProducao() : properties.getInsumoUrlHomologacao();
        return enviar(url, xmlRequest);
    }

    private String enviar(String url, String xmlRequest){
        try {
            String resposta = client(url).post()
                    .contentType(MediaType.TEXT_XML)
                    .body(xmlRequest)
                    .retrieve()
                    .body(String.class);
            verificarFault(resposta, 200);
            return resposta;
        } catch (RestClientResponseException e) {
            String corpo = e.getResponseBodyAsString();
            verificarFault(corpo, e.getStatusCode().value());
            throw new SpcApiException("Erro na API do SPC: " + e.getStatusCode() + " - " + corpo);
        }
    }

    private void verificarFault(String resposta, int status){
        if (resposta == null || !FAULT_TAG.matcher(resposta).find()) {
            return;
        }
        Matcher matcher = FAULT_STRING.matcher(resposta);
        String mensagem = matcher.find() ? matcher.group(1).trim() : resposta;
        throw new SpcApiException("Erro na API do SPC (HTTP " + status + "): " + mensagem);
    }
}
