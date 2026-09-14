package com.manager.app.spc;

import com.manager.app.spc.consulta.xsd.*;
import com.manager.app.spc.dto.SpcConsultaResponseDTO;
import com.manager.app.spc.dto.SpcInsumoResultadoDTO;
import com.manager.app.spc.dto.SpcResumoInsumoDTO;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

// mapeia a resposta gerada pelo JAXB (60+ tipos de insumo possiveis) pra uma estrutura
// generica via reflexao, em vez de uma classe Java por tipo - assim qualquer insumo que
// vier na resposta (obrigatorio ou opcional, previsto ou nao) aparece pro usuario
@Component
public class SpcInsumoGenericoMapper {

    // insumos de cheque ficam fora - loja nao trabalha com cheque
    private static final Set<String> INSUMOS_IGNORADOS = Set.of(
            "getCcf", "getChequeLojista", "getContraOrdem", "getContraOrdemDocumentoDiferente",
            "getContraOrdemAgenciaDiferente", "getContraOrdemAgenciaContaDiferente", "getContumacia",
            "getContumaciaSrs", "getChequeSemFundoAchei", "getChequeSemFundoAcheiCcf",
            "getChequeSemFundoVarejo", "getContraOrdemSrs", "getContraOrdemDocumentoDiferenteSrs",
            "getChequeOutrasOcorrenciasSrs", "getChequeConsultaOnlineSrs", "getConsultaRealizadaCheque"
    );

    public SpcConsultaResponseDTO mapear(ResultadoConsulta resultado){
        SpcConsultaResponseDTO dto = new SpcConsultaResponseDTO();
        if (resultado.getProtocolo() != null) {
            dto.setProtocoloNumero(resultado.getProtocolo().getNumero());
            dto.setProtocoloDigito(resultado.getProtocolo().getDigito());
        }
        dto.setRestricao(resultado.isRestricao());
        dto.setConsumidor(mapearConsumidor(resultado.getConsumidor()));
        dto.setAvisos(mapearAvisos(resultado));
        dto.setInsumos(mapearInsumos(resultado));
        return dto;
    }

    private Map<String, Object> mapearConsumidor(ResultadoConsumidor consumidor){
        if (consumidor == null) return Map.of();
        if (consumidor.getConsumidorPessoaFisica() != null) {
            return beanToMap(consumidor.getConsumidorPessoaFisica());
        }
        if (consumidor.getConsumidorPessoaJuridica() != null) {
            return beanToMap(consumidor.getConsumidorPessoaJuridica());
        }
        return Map.of();
    }

    private List<String> mapearAvisos(ResultadoConsulta resultado){
        List<String> avisos = new ArrayList<>();
        for (MensagemBaseExterna mensagem : resultado.getMensagemBaseExterna()) {
            avisos.add("Base " + mensagem.getOrigemBaseExterna() + " indisponível: " + mensagem.getMensagemBaseExterna());
        }
        for (String base : resultado.getBaseInoperante()) {
            avisos.add("Base inoperante: " + base);
        }
        for (MensagemComplementar complementar : resultado.getMensagemComplementar()) {
            avisos.addAll(complementar.getMensagem());
        }
        return avisos;
    }

    private List<SpcInsumoResultadoDTO> mapearInsumos(ResultadoConsulta resultado){
        List<SpcInsumoResultadoDTO> insumos = new ArrayList<>();
        for (Method metodo : ResultadoConsulta.class.getMethods()) {
            if (metodo.getParameterCount() != 0) continue;
            if (!ResultadoInsumo.class.isAssignableFrom(metodo.getReturnType())) continue;
            if (INSUMOS_IGNORADOS.contains(metodo.getName())) continue;

            ResultadoInsumo insumo;
            try {
                insumo = (ResultadoInsumo) metodo.invoke(resultado);
            } catch (Exception e) {
                continue;
            }
            if (insumo == null) continue;

            SpcInsumoResultadoDTO dto = new SpcInsumoResultadoDTO();
            dto.setNome(nomeInsumo(metodo.getName()));
            dto.setResumo(mapearResumo(insumo.getResumo()));
            dto.setDetalhes(mapearDetalhes(insumo));
            insumos.add(dto);
        }
        return insumos;
    }

    private SpcResumoInsumoDTO mapearResumo(ResumoInsumo resumo){
        if (resumo == null) return null;
        SpcResumoInsumoDTO dto = new SpcResumoInsumoDTO();
        dto.setQuantidadeTotal(resumo.getQuantidadeTotal());
        dto.setValorTotal(resumo.getValorTotal());
        dto.setValorUltimaOcorrencia(resumo.getValorUltimaOcorrencia());
        dto.setMesanoPrimeiraOcorrencia(resumo.getMesanoPrimeiraOcorrencia());
        dto.setMesanoUltimaOcorrencia(resumo.getMesanoUltimaOcorrencia());
        dto.setDataPrimeiraOcorrencia(formatarData(resumo.getDataPrimeiraOcorrencia()));
        dto.setDataUltimaOcorrencia(formatarData(resumo.getDataUltimaOcorrencia()));
        return dto;
    }

    private List<Map<String, Object>> mapearDetalhes(ResultadoInsumo insumo){
        List<Object> detalhes = insumo.getDetalheGrafiaPjOrDetalheSpcOrDetalheChequeLojista();
        List<Map<String, Object>> resultado = new ArrayList<>(detalhes.size());
        for (Object detalhe : detalhes) {
            resultado.add(beanToMap(detalhe));
        }
        return resultado;
    }

    // ---- reflexao genérica: qualquer objeto gerado pelo JAXB vira um Map<String,Object> ----

    private Map<String, Object> beanToMap(Object bean){
        if (bean == null) return Map.of();
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (Method metodo : bean.getClass().getMethods()) {
            if (metodo.getParameterCount() != 0) continue;
            if (metodo.getDeclaringClass() == Object.class) continue;
            String campo = nomeCampo(metodo.getName());
            if (campo == null) continue;

            Object valor;
            try {
                valor = metodo.invoke(bean);
            } catch (Exception e) {
                continue;
            }
            if (valor == null) continue;

            if (ehValorSimples(valor)) {
                mapa.put(campo, formatarValor(valor));
            } else if (valor instanceof List<?> lista) {
                if (!lista.isEmpty()) {
                    mapa.put(campo, lista.size() + " ocorrência(s)");
                }
            } else {
                for (Map.Entry<String, Object> entry : beanToMap(valor).entrySet()) {
                    mapa.put(campo + "." + entry.getKey(), entry.getValue());
                }
            }
        }
        return mapa;
    }

    private boolean ehValorSimples(Object valor){
        return valor instanceof String || valor instanceof Number || valor instanceof Boolean
                || valor instanceof XMLGregorianCalendar || valor instanceof Enum<?>;
    }

    private Object formatarValor(Object valor){
        if (valor instanceof XMLGregorianCalendar calendario) {
            return formatarData(calendario);
        }
        if (valor instanceof Enum<?> enumValor) {
            return enumValor.name();
        }
        return valor;
    }

    private String formatarData(XMLGregorianCalendar data){
        return data == null ? null : data.toXMLFormat();
    }

    private String nomeCampo(String nomeMetodo){
        String semPrefixo;
        if (nomeMetodo.startsWith("get") && nomeMetodo.length() > 3) {
            semPrefixo = nomeMetodo.substring(3);
        } else if (nomeMetodo.startsWith("is") && nomeMetodo.length() > 2) {
            semPrefixo = nomeMetodo.substring(2);
        } else {
            return null;
        }
        if (semPrefixo.equals("Class")) return null;
        return decapitalize(semPrefixo);
    }

    private String decapitalize(String texto){
        return Character.toLowerCase(texto.charAt(0)) + texto.substring(1);
    }

    // converte "SpcScore3Meses" (nome do getter) em "spc-score-3-meses" (nome do insumo no XML)
    private String nomeInsumo(String nomeMetodo){
        String semGet = nomeMetodo.startsWith("get") ? nomeMetodo.substring(3) : nomeMetodo;
        String comHifens = semGet
                .replaceAll("([0-9])([A-Z])", "$1-$2")
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("([A-Za-z])([0-9])", "$1-$2");
        return comHifens.toLowerCase();
    }
}
