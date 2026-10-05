package com.manager.app.backup;

import com.manager.app.backup.dto.BackupArquivoResponseDTO;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

// backup do PostgreSQL via pg_dump (formato custom, restaurável com pg_restore). Todos os
// backups - manuais e automáticos - ficam na mesma pasta e nunca são apagados por aqui
@Slf4j
@Service
public class BackupService {

    public static final String ORIGEM_MANUAL = "manual";
    public static final String ORIGEM_AUTOMATICO = "automatico";

    private static final DateTimeFormatter FORMATO_NOME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final long TIMEOUT_MINUTOS = 60;

    private final Path diretorio;
    private final String pgDumpConfigurado;
    private final String host;
    private final String porta;
    private final String banco;
    private final String usuario;
    private final String senha;

    // um backup por vez: o pg_dump é pesado e dois ao mesmo tempo só disputam disco
    private final ReentrantLock lock = new ReentrantLock();

    public BackupService(@Value("${backup.diretorio:../backups_bd}") String diretorio,
                         @Value("${backup.pg-dump-path:}") String pgDumpPath,
                         @Value("${spring.datasource.url}") String datasourceUrl,
                         @Value("${spring.datasource.username}") String usuario,
                         @Value("${spring.datasource.password:}") String senha){
        this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
        this.pgDumpConfigurado = pgDumpPath;
        this.usuario = usuario;
        this.senha = senha;

        // jdbc:postgresql://host:porta/banco?params
        URI uri = URI.create(datasourceUrl.replaceFirst("^jdbc:", ""));
        this.host = uri.getHost() != null ? uri.getHost() : "localhost";
        this.porta = String.valueOf(uri.getPort() > 0 ? uri.getPort() : 5432);
        this.banco = uri.getPath().replaceFirst("^/", "");

        try {
            Files.createDirectories(this.diretorio);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível preparar o diretório de backups: " + this.diretorio, e);
        }
    }

    public BackupArquivoResponseDTO executar(String origem){
        if (!lock.tryLock()) {
            throw new ItemInvalidoException("Já existe um backup em andamento. Aguarde terminar e tente novamente");
        }
        try {
            return gerar(origem);
        } finally {
            lock.unlock();
        }
    }

    private BackupArquivoResponseDTO gerar(String origem){
        String nome = banco + "_" + FORMATO_NOME.format(LocalDateTime.now()) + "_" + origem + ".dump";
        Path destino = diretorio.resolve(nome);
        // grava num .tmp e só renomeia no fim: nunca sobra um .dump pela metade na pasta
        Path temporario = diretorio.resolve(nome + ".tmp");

        List<String> comando = List.of(resolverPgDump(),
                "-h", host, "-p", porta, "-U", usuario,
                "-Fc", "--no-password", "-f", temporario.toString(), banco);

        ProcessBuilder builder = new ProcessBuilder(comando).redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", senha);

        try {
            Process processo = builder.start();
            String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!processo.waitFor(TIMEOUT_MINUTOS, TimeUnit.MINUTES)) {
                processo.destroyForcibly();
                throw new BackupException("O backup excedeu " + TIMEOUT_MINUTOS + " minutos e foi cancelado");
            }
            if (processo.exitValue() != 0) {
                log.error("pg_dump terminou com código {}: {}", processo.exitValue(), saida);
                throw new BackupException("Falha ao gerar o backup (pg_dump código " + processo.exitValue() + "): " + saida);
            }
            if (!Files.exists(temporario) || Files.size(temporario) == 0) {
                throw new BackupException("O pg_dump terminou sem gerar o arquivo de backup");
            }
            Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BackupException("Não foi possível executar o pg_dump (confira a instalação do PostgreSQL ou a propriedade backup.pg-dump-path): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackupException("Backup interrompido", e);
        } finally {
            try {
                Files.deleteIfExists(temporario);
            } catch (IOException ignorado) {
                // arquivo temporário órfão não afeta nada - só fica na pasta
            }
        }

        log.info("Backup ({}) gerado: {}", origem, destino);
        return paraDTO(destino);
    }

    public List<BackupArquivoResponseDTO> listar(){
        List<BackupArquivoResponseDTO> arquivos = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(diretorio, "*.dump")) {
            for (Path arquivo : stream) {
                arquivos.add(paraDTO(arquivo));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível listar os backups", e);
        }
        arquivos.sort(Comparator.comparing(BackupArquivoResponseDTO::criadoEm).reversed());
        return arquivos;
    }

    // proteção contra path traversal: nunca confiar num nome vindo do cliente sem normalizar e
    // conferir que o caminho resolvido continua dentro da pasta de backups
    public Path caminhoDoBackup(String nomeArquivo){
        if (nomeArquivo == null || !nomeArquivo.endsWith(".dump")
                || nomeArquivo.contains("/") || nomeArquivo.contains("\\") || nomeArquivo.contains("..")) {
            throw new ItemInvalidoException("Nome de arquivo de backup inválido");
        }
        Path resolvido = diretorio.resolve(nomeArquivo).normalize();
        if (!resolvido.startsWith(diretorio)) {
            throw new ItemInvalidoException("Nome de arquivo de backup inválido");
        }
        if (!Files.isRegularFile(resolvido)) {
            throw new ObjetoNaoEncontradoException("Backup não encontrado: " + nomeArquivo);
        }
        return resolvido;
    }

    private BackupArquivoResponseDTO paraDTO(Path arquivo){
        try {
            String nome = arquivo.getFileName().toString();
            LocalDateTime criadoEm = LocalDateTime.ofInstant(Files.getLastModifiedTime(arquivo).toInstant(), ZoneId.systemDefault());
            String origem = nome.endsWith("_" + ORIGEM_MANUAL + ".dump") ? ORIGEM_MANUAL : ORIGEM_AUTOMATICO;
            return new BackupArquivoResponseDTO(nome, Files.size(arquivo), criadoEm, origem);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o backup " + arquivo, e);
        }
    }

    // propriedade backup.pg-dump-path > pg_dump mais novo em C:\Program Files\PostgreSQL\<versão>\bin
    // > "pg_dump" do PATH (no Windows o instalador do PostgreSQL não adiciona ao PATH)
    private String resolverPgDump(){
        if (pgDumpConfigurado != null && !pgDumpConfigurado.isBlank()) {
            return pgDumpConfigurado;
        }
        Path instalacoes = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "PostgreSQL");
        if (Files.isDirectory(instalacoes)) {
            try (var versoes = Files.list(instalacoes)) {
                return versoes
                        .map(versao -> versao.resolve("bin").resolve("pg_dump.exe"))
                        .filter(Files::isRegularFile)
                        .max(Comparator.comparing(Path::toString))
                        .map(Path::toString)
                        .orElse("pg_dump");
            } catch (IOException e) {
                log.warn("Não foi possível procurar o pg_dump em {}", instalacoes, e);
            }
        }
        return "pg_dump";
    }
}
