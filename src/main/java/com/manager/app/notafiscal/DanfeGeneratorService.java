package com.manager.app.notafiscal;

import com.manager.app.shared.exception.ItemInvalidoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

// gera o PDF (DANFE, comprovante de cancelamento ou de carta de correção) chamando a API v2 do
// meudanfe.com.br (POST /fd/convert/xml-to-da: XML em texto plano + header Api-Key; devolve JSON
// com o PDF em base64; o tipo do documento é detectado por ela). Atenção: o XML carrega dados do
// destinatário (CPF, endereço) e, no caso de NF-e, fica armazenado na Área do Cliente deles
@Slf4j
@Service
public class DanfeGeneratorService {

    public enum TipoDocumento {
        NFE(null),
        CANCELAMENTO("110111"),
        CORRECAO("110110");

        private final String tpEvento;

        TipoDocumento(String tpEvento) {
            this.tpEvento = tpEvento;
        }
    }

    private static final byte[] CABECALHO_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private static final int TAMANHO_CACHE = 50;

    private final RestClient client;
    private final String apiKey;

    // últimos PDFs gerados (LRU em memória), chave = SHA-256 do XML. A API bloqueia a conta se o
    // mesmo XML for enviado várias vezes, então reabrir o mesmo documento nunca deve ir lá de novo
    private final Map<String, byte[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > TAMANHO_CACHE;
        }
    });

    public DanfeGeneratorService(@Value("${danfe.api.base-url:https://api.meudanfe.com.br/v2}") String baseUrl,
                                 @Value("${danfe.api.key:}") String apiKey,
                                 @Value("${danfe.api.timeout-segundos:30}") long timeoutSegundos){
        this.apiKey = apiKey;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    // gera o DANFE a partir de um XML avulso (não precisa existir uma NotaFiscal no banco pra
    // isso - o usuário pode enviar o XML de uma nota emitida fora deste sistema). Só aceita XML
    // já autorizado pela SEFAZ (nfeProc/protNFe), igual o manual da NFe exige pra emitir DANFE
    public byte[] gerarDanfePdf(byte[] xmlBytes){
        return gerarPdf(xmlBytes, TipoDocumento.NFE);
    }

    public byte[] gerarPdf(byte[] xmlBytes, TipoDocumento tipo){
        validar(xmlBytes, tipo);

        String chaveCache = hash(xmlBytes);
        byte[] emCache = cache.get(chaveCache);
        if (emCache != null) return emCache;

        byte[] pdf = chamarApi(xmlBytes);
        cache.put(chaveCache, pdf);
        return pdf;
    }

    private String hash(byte[] xmlBytes){
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xmlBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] chamarApi(byte[] xmlBytes){
        if (apiKey == null || apiKey.isBlank()) {
            throw new DanfeApiException("Chave da API de DANFE não configurada (propriedade danfe.api.key / variável DANFE_API_KEY)");
        }

        Map<String, Object> resposta;
        try {
            resposta = client.post()
                    .uri("/fd/convert/xml-to-da")
                    .header("Api-Key", apiKey)
                    // charset explícito: sem ele o Spring codifica text/plain em ISO-8859-1 e os
                    // acentos do XML (UTF-8) chegam como "�" no PDF (ex.: "SÃO MIGUEL" -> "S�O MIGUEL")
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new String(xmlBytes, StandardCharsets.UTF_8))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException e) {
            throw new DanfeApiException(mensagemDeErro(e), e);
        } catch (RestClientException e) {
            throw new DanfeApiException("Serviço de geração de DANFE indisponível no momento. Tente novamente em instantes", e);
        }

        Object data = resposta != null ? resposta.get("data") : null;
        if (data == null) {
            throw new DanfeApiException("O serviço de geração de DANFE não devolveu o PDF");
        }

        byte[] pdf;
        try {
            pdf = Base64.getMimeDecoder().decode(data.toString());
        } catch (IllegalArgumentException e) {
            throw new DanfeApiException("O serviço de geração de DANFE devolveu um PDF ilegível", e);
        }
        if (!comecaComPdf(pdf)) {
            throw new DanfeApiException("O serviço de geração de DANFE não devolveu um PDF válido");
        }
        return pdf;
    }

    private String mensagemDeErro(RestClientResponseException e){
        int status = e.getStatusCode().value();
        log.warn("API de DANFE respondeu HTTP {}: {}", status, e.getResponseBodyAsString());

        String motivo = switch (status) {
            case 400 -> "XML vazio, inválido ou de um tipo de documento não suportado";
            case 401 -> "chave da API de DANFE não informada ou inválida";
            case 402 -> "saldo insuficiente na conta da API de DANFE";
            case 403 -> "chave da API de DANFE foi substituída; atualize a configuração";
            case 429 -> "limite de requisições atingido, aguarde e tente novamente";
            default -> "erro no serviço; tente novamente mais tarde";
        };
        return "Falha ao gerar o DANFE (HTTP " + status + "): " + motivo;
    }


    private boolean comecaComPdf(byte[] pdf){
        if (pdf == null || pdf.length < CABECALHO_PDF.length) return false;
        for (int i = 0; i < CABECALHO_PDF.length; i++) {
            if (pdf[i] != CABECALHO_PDF[i]) return false;
        }
        return true;
    }

    private void validar(byte[] xmlBytes, TipoDocumento tipo){
        Document documento = parsear(new String(xmlBytes, StandardCharsets.UTF_8));

        if (tipo == TipoDocumento.NFE) {
            validarNfeAutorizada(documento);
            return;
        }
        validarEvento(documento, tipo);
    }

    private void validarNfeAutorizada(Document documento){
        if (!"nfeProc".equals(documento.getDocumentElement().getLocalName())) {
            throw new ItemInvalidoException("XML não é uma NFe autorizada (elemento raiz esperado: nfeProc)");
        }

        NodeList protNFe = documento.getElementsByTagNameNS("*", "protNFe");
        if (protNFe.getLength() == 0) {
            throw new ItemInvalidoException("XML não é uma NFe autorizada (protNFe ausente)");
        }

        NodeList chNFe = documento.getElementsByTagNameNS("*", "chNFe");
        NodeList nProt = documento.getElementsByTagNameNS("*", "nProt");
        if (chNFe.getLength() == 0 || nProt.getLength() == 0) {
            throw new ItemInvalidoException("XML não é uma NFe autorizada (chave de acesso ou protocolo de autorização ausente)");
        }
    }

    // evento de cancelamento (110111) ou carta de correção (110110) já homologado pela SEFAZ
    private void validarEvento(Document documento, TipoDocumento tipo){
        String nome = tipo == TipoDocumento.CANCELAMENTO ? "cancelamento" : "carta de correção";

        NodeList tpEvento = documento.getElementsByTagNameNS("*", "tpEvento");
        if (tpEvento.getLength() == 0 || !tipo.tpEvento.equals(tpEvento.item(0).getTextContent().trim())) {
            throw new ItemInvalidoException("XML não é de " + nome + " de NFe (evento " + tipo.tpEvento + " não encontrado)");
        }

        NodeList retEvento = documento.getElementsByTagNameNS("*", "retEvento");
        NodeList nProt = documento.getElementsByTagNameNS("*", "nProt");
        if (retEvento.getLength() == 0 || nProt.getLength() == 0) {
            throw new ItemInvalidoException("XML de " + nome + " sem o retorno da SEFAZ (protocolo ausente)");
        }
    }

    private Document parsear(String xml){
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new ItemInvalidoException("XML inválido ou malformado: " + e.getMessage());
        }
    }

    public String extrairChaveAcesso(byte[] xmlBytes){
        try {
            Document documento = parsear(new String(xmlBytes, StandardCharsets.UTF_8));
            NodeList chNFe = documento.getElementsByTagNameNS("*", "chNFe");
            return chNFe.getLength() > 0 ? chNFe.item(0).getTextContent() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
