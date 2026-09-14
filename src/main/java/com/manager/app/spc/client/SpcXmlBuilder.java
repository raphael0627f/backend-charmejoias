package com.manager.app.spc.client;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SpcXmlBuilder {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private String formatarData(LocalDate data){
        return data == null ? null : data.atStartOfDay().format(DATA_HORA);
    }

    // CDATA evita que nome/razao social/endereco com caracteres especiais (&, <, >, aspas) quebrem o XML
    private String cdata(String texto){
        return texto == null ? "" : "<![CDATA[" + texto + "]]>";
    }

    public record DadosPessoaFisica(String cpf, String nome){}
    public record DadosPessoaJuridica(String cnpj, String razaoSocial, String nomeComercial){}
    public record DadosEndereco(String cep, String logradouro, String bairro, String numero, String complemento){}

    public String montarInclusao(DadosPessoaFisica pessoaFisica, DadosPessoaJuridica pessoaJuridica,
                                  LocalDate dataCompra, LocalDate dataVencimento, String codigoTipoDevedor,
                                  String numeroContrato, BigDecimal valorDebito, Long naturezaInclusaoId,
                                  DadosEndereco endereco){
        StringBuilder dadosPessoa = new StringBuilder();
        if (pessoaFisica != null) {
            dadosPessoa.append("<tipo-pessoa>F</tipo-pessoa>")
                    .append("<dados-pessoa-fisica>")
                    .append("<cpf numero=\"").append(pessoaFisica.cpf()).append("\"/>")
                    .append("<nome>").append(cdata(pessoaFisica.nome())).append("</nome>")
                    .append("</dados-pessoa-fisica>");
        } else {
            dadosPessoa.append("<tipo-pessoa>J</tipo-pessoa>")
                    .append("<dados-pessoa-juridica>")
                    .append("<cnpj numero=\"").append(pessoaJuridica.cnpj()).append("\"/>")
                    .append("<razao-social>").append(cdata(pessoaJuridica.razaoSocial())).append("</razao-social>");
            if (pessoaJuridica.nomeComercial() != null) {
                dadosPessoa.append("<nome-comercial>").append(cdata(pessoaJuridica.nomeComercial())).append("</nome-comercial>");
            }
            dadosPessoa.append("</dados-pessoa-juridica>");
        }

        StringBuilder enderecoXml = new StringBuilder();
        if (endereco != null) {
            enderecoXml.append("<endereco-pessoa>")
                    .append("<cep>").append(endereco.cep()).append("</cep>")
                    .append("<logradouro>").append(cdata(endereco.logradouro())).append("</logradouro>")
                    .append("<bairro>").append(cdata(endereco.bairro())).append("</bairro>")
                    .append("<numero>").append(cdata(endereco.numero())).append("</numero>")
                    .append("<complemento>").append(cdata(endereco.complemento())).append("</complemento>")
                    .append("</endereco-pessoa>");
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:web="http://webservice.spc.insumo.spcjava.spcbrasil.org/">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <web:incluirSpc>
                         <insumoSpc>
                            %s
                            <data-compra>%s</data-compra>
                            <data-vencimento>%s</data-vencimento>
                            <codigo-tipo-devedor>%s</codigo-tipo-devedor>
                            <numero-contrato>%s</numero-contrato>
                            <valor-debito>%s</valor-debito>
                            <natureza-inclusao>
                               <id>%d</id>
                            </natureza-inclusao>
                            %s
                         </insumoSpc>
                      </web:incluirSpc>
                   </soapenv:Body>
                </soapenv:Envelope>""".formatted(dadosPessoa, formatarData(dataCompra), formatarData(dataVencimento),
                codigoTipoDevedor, cdata(numeroContrato), valorDebito, naturezaInclusaoId, enderecoXml);
    }

    public String montarExclusao(DadosPessoaFisica pessoaFisica, DadosPessoaJuridica pessoaJuridica,
                                  LocalDate dataVencimento, String codigoTipoDevedor, String numeroContrato,
                                  Long motivoExclusaoId){
        StringBuilder dadosPessoa = new StringBuilder();
        if (pessoaFisica != null) {
            dadosPessoa.append("<tipo-pessoa>F</tipo-pessoa>")
                    .append("<dados-pessoa-fisica>")
                    .append("<cpf numero=\"").append(pessoaFisica.cpf()).append("\"/>")
                    .append("</dados-pessoa-fisica>");
        } else {
            dadosPessoa.append("<tipo-pessoa>J</tipo-pessoa>")
                    .append("<dados-pessoa-juridica>")
                    .append("<cnpj numero=\"").append(pessoaJuridica.cnpj()).append("\"/>")
                    .append("</dados-pessoa-juridica>");
        }

        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:web="http://webservice.spc.insumo.spcjava.spcbrasil.org/">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <web:excluirSpc>
                      <excluir>
                        %s
                        <data-vencimento>%s</data-vencimento>
                        <codigo-tipo-devedor>%s</codigo-tipo-devedor>
                        <numero-contrato>%s</numero-contrato>
                        <motivo-exclusao>
                          <id>%d</id>
                        </motivo-exclusao>
                      </excluir>
                    </web:excluirSpc>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(dadosPessoa, formatarData(dataVencimento), codigoTipoDevedor,
                cdata(numeroContrato), motivoExclusaoId);
    }

    public String montarConsulta(Integer codigoProduto, String tipoPessoa, String documentoConsumidor, List<Integer> insumosOpcionais){
        StringBuilder insumos = new StringBuilder();
        if (insumosOpcionais != null) {
            for (Integer codigo : insumosOpcionais) {
                insumos.append("<codigo-insumo-opcional>").append(codigo).append("</codigo-insumo-opcional>");
            }
        }

        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:web="http://webservice.consulta.spcjava.spcbrasil.org/">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <web:filtro>
                         <codigo-produto>%d</codigo-produto>
                         <tipo-consumidor>%s</tipo-consumidor>
                         <documento-consumidor>%s</documento-consumidor>
                         %s
                      </web:filtro>
                   </soapenv:Body>
                </soapenv:Envelope>""".formatted(codigoProduto, tipoPessoa, documentoConsumidor, insumos);
    }
}
