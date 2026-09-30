package com.manager.app.notafiscal;

import com.manager.app.notafiscal.dto.XmlArquivoResponseDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class NotaFiscalXmlStorageService {

    private final Path diretorioBase;

    public NotaFiscalXmlStorageService(@Value("${app.storage.nfe-xml-dir}") String diretorio){
        this.diretorioBase = Path.of(diretorio);
        try {
            Files.createDirectories(diretorioBase);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível preparar o diretório de XMLs de NFe", e);
        }
    }

    public void salvar(NotaFiscal notaFiscal, byte[] xml){
        String nomeArquivo = notaFiscal.getNumero() + "-" + notaFiscal.getChaveAcesso() + "-NFe.xml";
        gravar(nomeArquivo, xml, notaFiscal.getId());
    }

    public void salvarCorrecao(NotaFiscal notaFiscal, Integer sequencia, byte[] xml){
        String nomeArquivo = "CCe " + sequencia + " - Nfe " + notaFiscal.getNumero() + ".xml";
        gravar(nomeArquivo, xml, notaFiscal.getId());
    }

    public void salvarCancelamento(NotaFiscal notaFiscal, byte[] xml){
        String nomeArquivo = "Cancelamento NFe-" + notaFiscal.getNumero() + ".xml";
        gravar(nomeArquivo, xml, notaFiscal.getId());
    }

    private void gravar(String nomeArquivo, byte[] xml, Long notaFiscalId){
        Path destino = diretorioBase.resolve(nomeArquivo);
        try {
            Files.write(destino, xml);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível salvar o XML da nota fiscal. id: " + notaFiscalId, e);
        }
    }

    // lista os XMLs já salvos no diretório - não tem tabela no banco por trás, é listagem de
    // diretório mesmo, paginada em memória. Se um dia a pasta crescer pra dezenas/centenas de
    // milhares de arquivos, essa abordagem em memória precisa ser revisitada; hoje (anos de
    // emissão diária) é tranquilo
    public Page<XmlArquivoResponseDTO> listar(Pageable pageable){
        List<XmlArquivoResponseDTO> arquivos = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(diretorioBase, "*.xml")) {
            for (Path arquivo : stream) {
                BasicFileAttributes atributos = Files.readAttributes(arquivo, BasicFileAttributes.class);
                LocalDateTime ultimaModificacao = LocalDateTime.ofInstant(atributos.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                arquivos.add(new XmlArquivoResponseDTO(arquivo.getFileName().toString(), atributos.size(), ultimaModificacao));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível listar os XMLs de NFe salvos", e);
        }

        arquivos.sort(Comparator.comparing(XmlArquivoResponseDTO::getUltimaModificacao).reversed());

        int inicio = (int) pageable.getOffset();
        if (inicio >= arquivos.size()) {
            return new PageImpl<>(List.of(), pageable, arquivos.size());
        }
        int fim = Math.min(inicio + pageable.getPageSize(), arquivos.size());
        return new PageImpl<>(arquivos.subList(inicio, fim), pageable, arquivos.size());
    }

    // proteção contra path traversal: nunca confiar num nome de arquivo vindo do cliente sem
    // normalizar e conferir que o caminho resolvido continua dentro do diretório base
    public byte[] carregar(String nomeArquivo){
        if (nomeArquivo == null || nomeArquivo.contains("/") || nomeArquivo.contains("\\") || nomeArquivo.contains("..")) {
            throw new ItemInvalidoException("Nome de arquivo inválido");
        }

        Path resolvido = diretorioBase.resolve(nomeArquivo).normalize();
        if (!resolvido.startsWith(diretorioBase.normalize())) {
            throw new ItemInvalidoException("Nome de arquivo inválido");
        }
        if (!Files.exists(resolvido) || !Files.isRegularFile(resolvido)) {
            throw new ObjetoNaoEncontradoException("Arquivo XML não encontrado: " + nomeArquivo);
        }

        try {
            return Files.readAllBytes(resolvido);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo XML: " + nomeArquivo, e);
        }
    }
}
