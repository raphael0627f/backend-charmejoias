package com.manager.app.notafiscal;

import br.com.swconsultoria.impressao.model.Impressao;
import br.com.swconsultoria.impressao.service.ImpressaoService;
import br.com.swconsultoria.impressao.util.ImpressaoUtil;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class DanfeGeneratorService {

    // logo da Charme Jóias no lugar do ícone padrão "NFe" que a biblioteca usa - o parâmetro
    // "Logo" é o mesmo nome que ImpressaoUtil.impressaoPadraoNFe já usa internamente pro ícone
    // padrão, só sobrescrevemos com a nossa imagem depois de montar a Impressao
    private static final String LOGO_DANFE = "/images/logo_charme.png";

    // gera o DANFE a partir de um XML avulso (não precisa existir uma NotaFiscal no banco pra
    // isso - o usuário pode enviar o XML de uma nota emitida fora deste sistema). Só aceita XML
    // já autorizado pela SEFAZ (nfeProc/protNFe), igual o manual da NFe exige pra emitir DANFE
    public byte[] gerarDanfePdf(byte[] xmlBytes){
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        validarNfeAutorizada(xml);

        try {
            Impressao impressao = ImpressaoUtil.impressaoPadraoNFe(xml);
            impressao.getParametros().put("Logo", getClass().getResourceAsStream(LOGO_DANFE));
            return ImpressaoService.impressaoPdfByte(impressao);
        } catch (Exception e) {
            throw new ItemInvalidoException("Não foi possível gerar o DANFE a partir do XML informado: " + e.getMessage());
        }
    }

    private void validarNfeAutorizada(String xml){
        Document documento = parsear(xml);

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
