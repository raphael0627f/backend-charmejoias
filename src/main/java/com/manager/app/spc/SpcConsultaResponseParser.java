package com.manager.app.spc;

import com.manager.app.spc.client.SpcApiException;
import com.manager.app.spc.consulta.xsd.ObjectFactory;
import com.manager.app.spc.consulta.xsd.ResultadoConsulta;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

@Component
public class SpcConsultaResponseParser {

    private final JAXBContext jaxbContext;

    public SpcConsultaResponseParser(){
        try {
            this.jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível inicializar o contexto JAXB do SPC", e);
        }
    }

    public ResultadoConsulta parse(String xmlResponse){
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            org.w3c.dom.Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlResponse)));

            Element body = primeiroElementoFilho(document.getDocumentElement());
            Element resultado = primeiroElementoFilho(body);

            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement<ResultadoConsulta> elemento = unmarshaller.unmarshal(resultado, ResultadoConsulta.class);
            return elemento.getValue();
        } catch (Exception e) {
            throw new SpcApiException("Não foi possível interpretar a resposta de consulta do SPC: " + e.getMessage());
        }
    }

    private Element primeiroElementoFilho(Node pai){
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node filho = filhos.item(i);
            if (filho.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) filho;
            }
        }
        throw new SpcApiException("Resposta de consulta do SPC sem o elemento esperado");
    }
}
