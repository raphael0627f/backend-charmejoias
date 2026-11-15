package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

// grava os anexos em disco, no mesmo padrão das imagens de produto (diretório configurável em app.storage); o banco guarda só os metadados
@Component
public class ContaPagarAnexoArmazenamento {

    private final Path base;

    public ContaPagarAnexoArmazenamento(@Value("${app.storage.financeiro-anexos-dir}") String diretorio) {
        this.base = Path.of(diretorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível preparar o diretório de anexos do financeiro", e);
        }
    }

    // devolve o caminho relativo gravado no banco: <idDoTitulo>/<uuid>.<extensao>
    public String salvar(Long tituloId, byte[] conteudo, String extensao) {
        String relativo = tituloId + "/" + UUID.randomUUID() + "." + extensao;
        Path destino = resolver(relativo);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o anexo", e);
        }
        return relativo;
    }

    // variante com pasta explícita (ex.: "receber/12" para os anexos do Contas a Receber); devolve <pasta>/<uuid>.<extensao>
    public String salvarEm(String pasta, byte[] conteudo, String extensao) {
        String relativo = pasta + "/" + UUID.randomUUID() + "." + extensao;
        Path destino = resolver(relativo);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o anexo", e);
        }
        return relativo;
    }

    public byte[] ler(String relativo) {
        try {
            return Files.readAllBytes(resolver(relativo));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o anexo", e);
        }
    }

    public void excluir(String relativo) {
        try {
            Files.deleteIfExists(resolver(relativo));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível remover o anexo", e);
        }
    }

    // impede sair do diretório base (nomes vêm do banco, mas a verificação protege contra dado corrompido)
    private Path resolver(String relativo) {
        Path destino = base.resolve(relativo).normalize();
        if (!destino.startsWith(base)) throw new ItemInvalidoException("Caminho de anexo inválido.");
        return destino;
    }
}
