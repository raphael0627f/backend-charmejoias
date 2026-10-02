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
        String nomeArquivo = notaFiscal.getNumero() + " - " + notaFiscal.getChaveAcesso() + "-NFe.xml";
        gravar(nomeArquivo, xml, notaFiscal.getId());
    }

    public void salvarCorrecao(NotaFiscal notaFiscal, Integer sequencia, byte[] xml){
        gravar(nomeArquivoCorrecao(notaFiscal.getNumero(), sequencia), xml, notaFiscal.getId());
    }

    public void salvarCancelamento(NotaFiscal notaFiscal, byte[] xml){
        gravar(nomeArquivoCancelamento(notaFiscal.getNumero()), xml, notaFiscal.getId());
    }

    public byte[] carregarCorrecao(Long numero, Integer sequencia){
        return carregar(nomeArquivoCorrecao(numero, sequencia));
    }

    public byte[] carregarCancelamento(Long numero){
        return carregar(nomeArquivoCancelamento(numero));
    }

    private String nomeArquivoCorrecao(Long numero, Integer sequencia){
        return "CCe " + sequencia + " - Nfe " + numero + ".xml";
    }

    private String nomeArquivoCancelamento(Long numero){
        return "Cancelamento NFe-" + numero + ".xml";
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

    // busca o XML da NFe pelo número no início do nome do arquivo, sem exigir bater a chave de
    // acesso exata - usado no "Baixar DANFE" pra achar o XML mesmo que a chave salva no banco
    // divirja por algum motivo do nome real do arquivo em disco. Aceita qualquer espaçamento em
    // volta do hífen (ex.: "5328 -...", "5328 - ...", "5328-...") já que arquivos salvos em
    // versões diferentes do sistema não seguem sempre o mesmo padrão exato
    public byte[] carregarPorNumero(Long numero){
        java.util.regex.Pattern padrao = java.util.regex.Pattern.compile(
                "^" + numero + "\\s*-\\s*.*-NFe\\.xml$");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(diretorioBase, "*-NFe.xml")) {
            for (Path arquivo : stream) {
                if (padrao.matcher(arquivo.getFileName().toString()).matches()) {
                    return Files.readAllBytes(arquivo);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível buscar o XML da nota fiscal. número: " + numero, e);
        }
        throw new ObjetoNaoEncontradoException("Arquivo XML não encontrado para a nota número " + numero);
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
