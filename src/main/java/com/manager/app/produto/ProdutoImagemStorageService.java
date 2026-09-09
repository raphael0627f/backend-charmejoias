package com.manager.app.produto;

import com.manager.app.shared.NanoIdUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class ProdutoImagemStorageService {

    private static final Set<String> CONTENT_TYPES_PERMITIDOS = Set.of("image/png", "image/jpeg", "image/webp");

    private final Path diretorioBase;

    public ProdutoImagemStorageService(@Value("${app.storage.produtos-imagens-dir}") String diretorio){
        this.diretorioBase = Path.of(diretorio);
        try {
            Files.createDirectories(diretorioBase);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível preparar o diretório de imagens de produtos", e);
        }
    }

    public String salvar(UUID produtoId, MultipartFile arquivo){
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ItemInvalidoException("Arquivo de imagem é obrigatório");
        }
        String contentType = arquivo.getContentType();
        if (contentType == null || !CONTENT_TYPES_PERMITIDOS.contains(contentType)) {
            throw new ItemInvalidoException("Tipo de arquivo não permitido. Use PNG, JPEG ou WEBP");
        }

        String extensao = switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "bin";
        };

        String nomeArquivo = produtoId + "-" + NanoIdUtils.generate(8) + "." + extensao;
        Path destino = diretorioBase.resolve(nomeArquivo);

        try {
            Files.copy(arquivo.getInputStream(), destino);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível salvar a imagem do produto", e);
        }

        return "/produtos/" + produtoId + "/imagem/" + nomeArquivo;
    }

    public byte[] carregar(String imagemUrl){
        Path arquivo = resolverArquivo(imagemUrl);
        try {
            return Files.readAllBytes(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a imagem do produto", e);
        }
    }

    public void excluir(String imagemUrl){
        if (!StringUtils.hasText(imagemUrl)) return;
        try {
            Files.deleteIfExists(resolverArquivo(imagemUrl));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível remover a imagem do produto", e);
        }
    }

    private Path resolverArquivo(String imagemUrl){
        String nomeArquivo = imagemUrl.substring(imagemUrl.lastIndexOf('/') + 1);
        return diretorioBase.resolve(nomeArquivo);
    }
}
