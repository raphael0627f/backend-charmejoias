package com.manager.app.notafiscal;

import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// monta, em memoria, o zip com os XMLs das notas: uma pasta por nota (NF-<numero>/) com o XML autorizado, o do
// cancelamento e os das cartas de correcao aceitas. Nota sem nenhum XML disponivel fica fora do zip.
@Service
@RequiredArgsConstructor
public class NotaFiscalXmlZipService {

    private final NotaFiscalRepository repository;
    private final NotaFiscalXmlStorageService xmlStorage;

    @Value("${app.mail.max-zip-bytes:20971520}")
    private long maxZipBytes;

    public record XmlZip(byte[] conteudo, List<Long> incluidas, List<Long> ignoradasSemXml) {}

    @Transactional(readOnly = true)
    public XmlZip montar(List<Long> notaIds){
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(notaIds));
        List<NotaFiscal> notas = new ArrayList<>(repository.findAllById(ids));
        if (notas.size() != ids.size()) {
            Set<Long> encontrados = new HashSet<>();
            notas.forEach(n -> encontrados.add(n.getId()));
            Long faltante = ids.stream().filter(id -> !encontrados.contains(id)).findFirst().orElse(null);
            throw new ObjetoNaoEncontradoException("Nota fiscal não encontrada. Id: " + faltante);
        }
        // mesma ordem em que o usuario selecionou
        notas.sort(Comparator.comparingInt(n -> ids.indexOf(n.getId())));

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        List<Long> incluidas = new ArrayList<>();
        List<Long> ignoradas = new ArrayList<>();
        Set<String> pastas = new HashSet<>();

        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            for (NotaFiscal nota : notas) {
                if (nota.getNumero() == null) {
                    throw new ItemInvalidoException("Selecione apenas notas fiscais já emitidas: há nota sem número (rascunho). Id: " + nota.getId());
                }
                String pasta = pastaUnica(nota, pastas);
                boolean algum = false;

                byte[] autorizado = carregarOuNulo(() -> xmlStorage.carregarPorNumero(nota.getNumero()));
                if (autorizado != null) {
                    boolean temChave = nota.getChaveAcesso() != null && !nota.getChaveAcesso().isBlank();
                    adicionar(zip, pasta + (temChave ? nota.getChaveAcesso() + "-nfe.xml" : "nfe.xml"), autorizado);
                    algum = true;
                }
                if (nota.getStatus() == StatusNotaFiscal.CANCELADA) {
                    byte[] cancelamento = carregarOuNulo(() -> xmlStorage.carregarCancelamento(nota.getNumero()));
                    if (cancelamento != null) {
                        adicionar(zip, pasta + "cancelamento.xml", cancelamento);
                        algum = true;
                    }
                }
                for (NotaFiscalCorrecao correcao : nota.getCorrecoes()) {
                    if (correcao.getStatus() != StatusCorrecao.ACEITA || correcao.getSequencia() == null) continue;
                    byte[] cce = carregarOuNulo(() -> xmlStorage.carregarCorrecao(nota.getNumero(), correcao.getSequencia()));
                    if (cce != null) {
                        adicionar(zip, pasta + "cce-" + correcao.getSequencia() + ".xml", cce);
                        algum = true;
                    }
                }

                if (algum) {
                    incluidas.add(nota.getNumero());
                } else {
                    ignoradas.add(nota.getNumero());
                }
                zip.flush();
                if (saida.size() > maxZipBytes) {
                    throw excedeLimite();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível montar o zip dos XMLs das notas fiscais", e);
        }

        if (saida.size() > maxZipBytes) {
            throw excedeLimite();
        }
        return new XmlZip(saida.toByteArray(), incluidas, ignoradas);
    }

    private ItemInvalidoException excedeLimite(){
        long mb = maxZipBytes / (1024 * 1024);
        return new ItemInvalidoException("O arquivo zip com os XMLs excede o tamanho máximo de " + mb + " MB. Divida o envio em partes, selecionando menos notas.");
    }

    // pasta NF-<numero>/; se outra nota selecionada tiver o mesmo numero (outra serie/empresa), acrescenta o id para nao sobrescrever
    private String pastaUnica(NotaFiscal nota, Set<String> pastas){
        String pasta = "NF-" + nota.getNumero() + "/";
        if (!pastas.add(pasta)) {
            pasta = "NF-" + nota.getNumero() + "-" + nota.getId() + "/";
            pastas.add(pasta);
        }
        return pasta;
    }

    private void adicionar(ZipOutputStream zip, String nome, byte[] conteudo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        zip.write(conteudo);
        zip.closeEntry();
    }

    private byte[] carregarOuNulo(Supplier<byte[]> carga){
        try {
            return carga.get();
        } catch (ObjetoNaoEncontradoException e) {
            return null;
        }
    }
}
