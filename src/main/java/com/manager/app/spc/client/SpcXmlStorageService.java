package com.manager.app.spc.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SpcXmlStorageService {

    private final Path diretorioBase;

    public SpcXmlStorageService(@Value("${app.storage.spc-xml-dir}") String diretorio){
        this.diretorioBase = Path.of(diretorio);
        try {
            Files.createDirectories(diretorioBase);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível preparar o diretório de XMLs do SPC", e);
        }
    }

    public void salvarConsulta(Object id, String xmlRequest, String xmlResponse){
        gravar(id + "-consulta-request.xml", xmlRequest);
        gravar(id + "-consulta-response.xml", xmlResponse);
    }

    public void salvarInclusao(Object id, String xmlRequest, String xmlResponse){
        gravar(id + "-inclusao-request.xml", xmlRequest);
        gravar(id + "-inclusao-response.xml", xmlResponse);
    }

    public void salvarExclusao(Object id, String xmlRequest, String xmlResponse){
        gravar(id + "-exclusao-request.xml", xmlRequest);
        gravar(id + "-exclusao-response.xml", xmlResponse);
    }

    public String lerRespostaConsulta(Object id){
        Path arquivo = diretorioBase.resolve(id + "-consulta-response.xml");
        try {
            return Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o XML da consulta SPC. id: " + id, e);
        }
    }

    private void gravar(String nomeArquivo, String xml){
        Path destino = diretorioBase.resolve(nomeArquivo);
        try {
            Files.writeString(destino, xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível salvar o XML do SPC: " + nomeArquivo, e);
        }
    }
}
