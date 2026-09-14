package com.manager.app.notafiscal.client;

import com.manager.app.notafiscal.tabela.AliquotaIcmsTable;
import com.manager.app.notafiscal.AmbienteNfe;
import com.manager.app.empresa.RegimeTributario;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.manager.app.cliente.Cliente;
import com.manager.app.empresa.Empresa;
import com.manager.app.endereco.Endereco;
import com.manager.app.notafiscal.NotaFiscal;
import com.manager.app.notafiscal.NotaFiscalItem;
import com.manager.app.notafiscal.NotaFiscalPagamento;
import com.manager.app.produto.Produto;
import com.manager.app.transportadora.Transportadora;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ContoraClient {

    private final ContoraProperties properties;
    private final ObjectMapper objectMapper;

    private RestClient client(){
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();
    }

    public String criarEmpresa(Empresa empresa){
        String contoraCompanyId;
        try {
            Map<String, Object> response = client().post()
                    .uri("/companies")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloadEmpresaCriacao(empresa))
                    .retrieve()
                    .body(Map.class);
            contoraCompanyId = extrairId(response);
        } catch (RestClientResponseException e) {
            String existente = extrairCompanyIdDeConflito(e);
            if (existente == null) {
                throw new ContoraApiException("Erro na API da Contora: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            }
            // CNPJ já cadastrado manualmente no painel da Contora - reaproveita o cadastro existente
            contoraCompanyId = existente;
        }

        // allows_homologation só faz sentido pra empresa cadastrada em produção
        // (deixa a mesma chave emitir espelhos de teste); empresa já criada em homologação não precisa disso
        if ("producao".equals(properties.getDefaultEnvironment())) {
            client().patch()
                    .uri("/companies/{id}", contoraCompanyId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("allows_homologation", true))
                    .retrieve()
                    .toBodilessEntity();
        }

        return contoraCompanyId;
    }

    public void atualizarEmpresa(String contoraCompanyId, Empresa empresa){
        executar(() -> client().patch()
                .uri("/companies/{id}", contoraCompanyId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloadEmpresaComum(empresa))
                .retrieve()
                .body(Map.class));
    }

    public void enviarCertificado(String contoraCompanyId, MultipartFile certificado, String senha){
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("certificate", new ByteArrayResource(certificado.getBytes()) {
                @Override
                public String getFilename() {
                    return certificado.getOriginalFilename();
                }
            });
        } catch (IOException e) {
            throw new ContoraApiException("Falha ao ler o arquivo do certificado");
        }
        builder.part("password", senha);

        executar(() -> client().post()
                .uri("/companies/{id}/certificate", contoraCompanyId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(Map.class));
    }

    private static final Set<String> CST_ICMS_COM_DESTAQUE = Set.of("00", "10", "20", "70", "90");
    private static final Set<String> CST_PIS_COFINS_TRIBUTADO = Set.of("01", "02");

    public String criarRascunhoNfe(NotaFiscal notaFiscal){
        String contoraCompanyId = notaFiscal.getEmpresa().getContoraCompanyId();
        Map<String, Object> response = executar(() -> client().post()
                .uri("/companies/{id}/nfe/drafts", contoraCompanyId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "nota-" + notaFiscal.getId() + "-" + notaFiscal.getNumeroTentativaEnvio() + "-create")
                .body(payloadDraft(notaFiscal))
                .retrieve()
                .body(Map.class));
        return extrairId(response);
    }

    public void buildRascunho(String contoraCompanyId, String documentId, Long notaFiscalId, Integer tentativa){
        executar(() -> client().post()
                .uri("/companies/{companyId}/nfe/drafts/{documentId}/build", contoraCompanyId, documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "nota-" + notaFiscalId + "-" + tentativa + "-build")
                .body(Map.of("sign", false))
                .retrieve()
                .body(Map.class));
    }

    public void autorizarRascunho(String contoraCompanyId, String documentId, Long notaFiscalId, Integer tentativa){
        executar(() -> client().post()
                .uri("/companies/{companyId}/nfe/drafts/{documentId}/dispatch", contoraCompanyId, documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "nota-" + notaFiscalId + "-" + tentativa + "-authorize")
                .body(Map.of("action", "authorize"))
                .retrieve()
                .body(Map.class));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> consultarStatus(String contoraCompanyId, String documentId){
        Map<String, Object> response = executar(() -> client().get()
                .uri("/companies/{companyId}/nfe/drafts/{documentId}/status", contoraCompanyId, documentId)
                .retrieve()
                .body(Map.class));
        Object data = response != null ? response.get("data") : null;
        return data instanceof Map<?, ?> dataMap ? (Map<String, Object>) dataMap : Map.of();
    }

    public void cancelar(String contoraCompanyId, String documentId, String motivo){
        executar(() -> client().post()
                .uri("/companies/{companyId}/nfe/drafts/{documentId}/cancel", contoraCompanyId, documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", motivo))
                .retrieve()
                .body(Map.class));
    }

    public void corrigir(String contoraCompanyId, String documentId, String texto){
        executar(() -> client().post()
                .uri("/companies/{companyId}/nfe/drafts/{documentId}/corrections", contoraCompanyId, documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", texto))
                .retrieve()
                .body(Map.class));
    }

    public byte[] baixarArtefato(String contoraCompanyId, String documentId, String artefato){
        return executar(() -> client().get()
                .uri("/companies/{companyId}/nfe/drafts/{documentId}/artifacts/{artefato}", contoraCompanyId, documentId, artefato)
                .retrieve()
                .body(byte[].class));
    }

    private Map<String, Object> payloadDraft(NotaFiscal notaFiscal){
        Empresa empresa = notaFiscal.getEmpresa();

        Map<String, Object> body = new HashMap<>();
        body.put("document_type", "nfe");
        body.put("environment", notaFiscal.getAmbiente() == AmbienteNfe.HOMOLOGACAO ? "homologacao" : "producao");
        body.put("series", notaFiscal.getSerie());
        body.put("number", notaFiscal.getNumero());
        body.put("payload", payloadNota(notaFiscal, empresa));
        return body;
    }

    // texto exigido pela Resolução CGSN nº 10/2007, art. 2º, § 2º (redação da Resolução CGSN nº 53/2008)
    // em toda nota emitida por ME/EPP optante pelo Simples Nacional
    private static final String AVISO_SIMPLES_NACIONAL =
            "DOCUMENTO EMITIDO POR ME OU EPP OPTANTE PELO SIMPLES NACIONAL. NÃO GERA DIREITO A CRÉDITO FISCAL DE IPI. " +
            "(Resolução CGSN nº 10/2007, art. 2º, § 2º, na redação dada pela Resolução CGSN nº 53/2008)";

    private Map<String, Object> payloadNota(NotaFiscal notaFiscal, Empresa empresa){
        Map<String, Object> payload = new HashMap<>();
        payload.put("nature_operation", notaFiscal.getNaturezaOperacao());
        payload.put("operation_type", "saida");
        payload.put("consumer_final", notaFiscal.isConsumidorFinal());
        payload.put("presence_indicator", notaFiscal.getIndicadorPresenca());
        payload.put("recipient", payloadDestinatario(notaFiscal.getCliente()));
        payload.put("items", notaFiscal.getItens().stream().map(item -> payloadItem(item, empresa, notaFiscal.getCliente())).toList());
        payload.put("payments", notaFiscal.getPagamentos().stream().map(this::payloadPagamento).toList());

        if (notaFiscal.getDataSaidaEntrada() != null) {
            payload.put("exit_at", notaFiscal.getDataSaidaEntrada().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        Map<String, Object> shipping = payloadShipping(notaFiscal);
        if (shipping != null) {
            payload.put("shipping", shipping);
        }

        String informacoesAdicionais = payloadInformacoesAdicionais(notaFiscal, empresa);
        if (informacoesAdicionais != null) {
            payload.put("additional_info", informacoesAdicionais);
        }

        return payload;
    }

    private Map<String, Object> payloadShipping(NotaFiscal notaFiscal){
        if (notaFiscal.getTransportadora() == null && notaFiscal.getModalidadeFrete() == null) {
            return null;
        }

        Map<String, Object> shipping = new HashMap<>();
        if (notaFiscal.getModalidadeFrete() != null) {
            shipping.put("modality", notaFiscal.getModalidadeFrete());
        }

        Transportadora transportadora = notaFiscal.getTransportadora();
        if (transportadora != null) {
            Map<String, Object> carrier = new HashMap<>();
            carrier.put("name", transportadora.getRazaoSocial());
            carrier.put("document", transportadora.getDocumento());
            carrier.put("state_registration", transportadora.getInscricaoEstadual());
            carrier.put("address", transportadora.getEndereco());
            carrier.put("city_name", transportadora.getMunicipio());
            carrier.put("state_code", transportadora.getUf());
            shipping.put("carrier", carrier);

            if (transportadora.getPlacaVeiculo() != null && !transportadora.getPlacaVeiculo().isBlank()) {
                Map<String, Object> vehicle = new HashMap<>();
                vehicle.put("plate", transportadora.getPlacaVeiculo());
                vehicle.put("state_code", transportadora.getUfVeiculo());
                vehicle.put("rntc", transportadora.getRntc());
                shipping.put("vehicle", vehicle);
            }
        }

        return shipping;
    }

    private String payloadInformacoesAdicionais(NotaFiscal notaFiscal, Empresa empresa){
        boolean simplesNacional = empresa.getRegimeTributario() == RegimeTributario.SIMPLES_NACIONAL
                || empresa.getRegimeTributario() == RegimeTributario.SIMPLES_NACIONAL_EXCESSO_SUBLIMITE;

        StringBuilder texto = new StringBuilder();
        if (simplesNacional) {
            texto.append(AVISO_SIMPLES_NACIONAL);
        }
        if (notaFiscal.getInformacoesComplementares() != null && !notaFiscal.getInformacoesComplementares().isBlank()) {
            if (!texto.isEmpty()) {
                texto.append("\n");
            }
            texto.append(notaFiscal.getInformacoesComplementares());
        }

        return !texto.isEmpty() ? texto.toString() : null;
    }

    private Map<String, Object> payloadDestinatario(Cliente cliente){
        Endereco endereco = cliente.getEnderecoPrincipal();

        Map<String, Object> address = new HashMap<>();
        address.put("street", endereco.getLogradouro());
        address.put("number", endereco.getNumero());
        address.put("district", endereco.getBairro());
        address.put("city_name", endereco.getMunicipio());
        address.put("city_code", endereco.getIbge());
        address.put("state_code", endereco.getUf());
        address.put("postal_code", endereco.getCep());

        Map<String, Object> recipient = new HashMap<>();
        recipient.put("name", cliente.getNome());
        recipient.put("document", cliente.getDocumento() != null ? cliente.getDocumento() : cliente.getCpf());
        recipient.put("state_registration_indicator", 9);
        if (cliente.getEmail() != null && !cliente.getEmail().isBlank()) {
            recipient.put("email", cliente.getEmail());
        }
        recipient.put("address", address);
        return recipient;
    }

    private Map<String, Object> payloadItem(NotaFiscalItem item, Empresa empresa, Cliente cliente){
        Produto produto = item.getProduto();

        Map<String, Object> map = new HashMap<>();
        map.put("code", produto.getSku());
        map.put("name", produto.getNome());
        map.put("ncm", produto.getNcm() != null ? produto.getNcm().getCodigo() : null);
        map.put("cfop", item.getCfop());
        map.put("unit", produto.getUnidadeComercial());
        map.put("quantity", item.getQuantidade());
        map.put("unit_price", item.getValorUnitario());
        if (item.getDesconto() != null) {
            map.put("discount", item.getDesconto());
        }
        if (produto.getNcm() != null && produto.getNcm().getAliquotaAproximadaTributos() != null) {
            BigDecimal aliquota = produto.getNcm().getAliquotaAproximadaTributos();
            map.put("approximate_taxes", item.getValorTotal().multiply(aliquota).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP));
        }
        map.put("taxes", payloadImpostos(item, empresa, cliente));
        return map;
    }

    private Map<String, Object> payloadImpostos(NotaFiscalItem item, Empresa empresa, Cliente cliente){
        Produto produto = item.getProduto();
        boolean regimeNormal = empresa.getRegimeTributario() == RegimeTributario.LUCRO_PRESUMIDO
                || empresa.getRegimeTributario() == RegimeTributario.LUCRO_REAL;

        Map<String, Object> icms = new HashMap<>();
        icms.put("code", produto.getCodigoSituacaoTributariaIcms());
        icms.put("origin", produto.getOrigemProduto());
        if (regimeNormal && CST_ICMS_COM_DESTAQUE.contains(produto.getCodigoSituacaoTributariaIcms())) {
            boolean interestadual = item.getCfop() != null && item.getCfop().startsWith("6");
            BigDecimal aliquota = interestadual
                    ? AliquotaIcmsTable.aliquotaInterestadual(empresa.getEndereco().getUf(), cliente.getEnderecoPrincipal().getUf())
                    : AliquotaIcmsTable.aliquotaInterna(empresa.getEndereco().getUf());
            icms.put("aliquot", aliquota);
        }

        Map<String, Object> pis = new HashMap<>();
        pis.put("code", produto.getCodigoSituacaoTributariaPis());
        Map<String, Object> cofins = new HashMap<>();
        cofins.put("code", produto.getCodigoSituacaoTributariaCofins());

        if (regimeNormal && CST_PIS_COFINS_TRIBUTADO.contains(produto.getCodigoSituacaoTributariaPis())) {
            boolean naoCumulativo = empresa.getRegimeTributario() == RegimeTributario.LUCRO_REAL;
            pis.put("aliquot", naoCumulativo ? new BigDecimal("1.65") : new BigDecimal("0.65"));
        }
        if (regimeNormal && CST_PIS_COFINS_TRIBUTADO.contains(produto.getCodigoSituacaoTributariaCofins())) {
            boolean naoCumulativo = empresa.getRegimeTributario() == RegimeTributario.LUCRO_REAL;
            cofins.put("aliquot", naoCumulativo ? new BigDecimal("7.6") : new BigDecimal("3"));
        }

        Map<String, Object> taxes = new HashMap<>();
        taxes.put("icms", icms);
        taxes.put("pis", pis);
        taxes.put("cofins", cofins);
        return taxes;
    }

    private Map<String, Object> payloadPagamento(NotaFiscalPagamento pagamento){
        Map<String, Object> map = new HashMap<>();
        map.put("method", pagamento.getFormaPagamento());
        map.put("amount", pagamento.getValor());
        return map;
    }

    private Map<String, Object> payloadEmpresaComum(Empresa empresa){
        Map<String, Object> payload = new HashMap<>();
        payload.put("legal_name", empresa.getRazaoSocial());
        payload.put("trade_name", empresa.getNomeFantasia());
        payload.put("tax_regime", mapearRegime(empresa.getRegimeTributario()));
        payload.put("state_registration", empresa.getInscricaoEstadual());
        payload.put("phone", empresa.getTelefone());
        payload.put("street", empresa.getEndereco().getLogradouro());
        payload.put("number", empresa.getEndereco().getNumero());
        payload.put("district", empresa.getEndereco().getBairro());
        payload.put("city_name", empresa.getEndereco().getMunicipio());
        payload.put("city_code", empresa.getEndereco().getIbge());
        payload.put("state_code", empresa.getEndereco().getUf());
        payload.put("postal_code", empresa.getEndereco().getCep());
        return payload;
    }

    // POST /companies aceita document e default_environment; PATCH não declara essas propriedades
    private Map<String, Object> payloadEmpresaCriacao(Empresa empresa){
        Map<String, Object> payload = payloadEmpresaComum(empresa);
        payload.put("document", empresa.getCnpj());
        payload.put("default_environment", properties.getDefaultEnvironment());
        return payload;
    }

    private String mapearRegime(RegimeTributario regime){
        if (regime == null) return null;
        return switch (regime) {
            case SIMPLES_NACIONAL, SIMPLES_NACIONAL_EXCESSO_SUBLIMITE -> "simples";
            case LUCRO_PRESUMIDO -> "presumido";
            case LUCRO_REAL -> "real";
        };
    }

    private String extrairCompanyIdDeConflito(RestClientResponseException e){
        if (e.getStatusCode() != HttpStatus.CONFLICT) return null;
        try {
            JsonNode body = objectMapper.readTree(e.getResponseBodyAsString());
            JsonNode error = body.get("error");
            if (error == null || !"company_already_exists".equals(error.path("type").asString(null))) {
                return null;
            }
            JsonNode companyId = error.path("details").path("company_id");
            return companyId.isMissingNode() ? null : companyId.asString(null);
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extrairId(Map<String, Object> response){
        Object data = response != null ? response.get("data") : null;
        if (data instanceof Map<?, ?> dataMap && dataMap.get("id") != null) {
            return dataMap.get("id").toString();
        }
        throw new ContoraApiException("Resposta da Contora sem o id da empresa criada");
    }

    private <T> T executar(java.util.function.Supplier<T> chamada){
        try {
            return chamada.get();
        } catch (RestClientResponseException e) {
            throw new ContoraApiException("Erro na API da Contora: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        }
    }
}
