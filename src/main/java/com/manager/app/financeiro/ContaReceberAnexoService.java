package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.ContaReceberAnexoDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Collection;

// anexos do título a receber (documento) e comprovantes de recebimento. O título é bloqueado (SELECT FOR UPDATE) para que o limite de 10 anexos
// valha mesmo com envios simultâneos. Os arquivos ficam na subpasta receber/{idDoTitulo} do diretório de anexos do financeiro.
@Service
@RequiredArgsConstructor
public class ContaReceberAnexoService {

    private final ContaReceberAnexoRepository repository;
    private final ContaReceberRepository contaReceberRepository;
    private final ContaReceberRecebimentoRepository recebimentoRepository;
    private final ContaReceberEventoRepository eventoRepository;
    private final ContaPagarAnexoArmazenamento armazenamento;
    private final ContaReceberMapper mapper;
    private final UsuarioAuditorAware auditor;

    public record Download(String nome, String tipoConteudo, byte[] conteudo) {
    }

    @Transactional
    public ContaReceberAnexoDTO anexarAoTitulo(Long tituloId, TipoAnexo tipo, MultipartFile arquivo) {
        ContaReceberAnexoCalculo.validarTipoNoTitulo(tipo);
        byte[] conteudo = ler(arquivo);
        AnexoValidacao.Validado v = AnexoValidacao.validar(conteudo, arquivo.getOriginalFilename());
        ContaReceber titulo = contaReceberRepository.findByIdForUpdate(tituloId).orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
        return gravar(titulo, null, TipoAnexo.DOCUMENTO, v, conteudo);
    }

    @Transactional
    public ContaReceberAnexoDTO anexarComprovante(Long recebimentoId, MultipartFile arquivo) {
        byte[] conteudo = ler(arquivo);
        AnexoValidacao.Validado v = AnexoValidacao.validar(conteudo, arquivo.getOriginalFilename());
        Long tituloId = recebimentoRepository.findContaReceberIdById(recebimentoId).orElseThrow(() -> new ObjetoNaoEncontradoException("Recebimento não encontrado."));
        ContaReceber titulo = contaReceberRepository.findByIdForUpdate(tituloId).orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
        ContaReceberRecebimento recebimento = recebimentoRepository.findById(recebimentoId).orElseThrow(() -> new ObjetoNaoEncontradoException("Recebimento não encontrado."));
        ContaReceberAnexoCalculo.validarRecebimentoAceitaComprovante(recebimento.isEstornada());
        return gravar(titulo, recebimento, TipoAnexo.COMPROVANTE, v, conteudo);
    }

    private ContaReceberAnexoDTO gravar(ContaReceber titulo, ContaReceberRecebimento recebimento, TipoAnexo tipo, AnexoValidacao.Validado v, byte[] conteudo) {
        ContaReceberAnexoCalculo.validarAnexavel(titulo.getSituacao(), repository.countByContaReceberId(titulo.getId()));
        ContaReceberAnexo a = new ContaReceberAnexo();
        a.setContaReceber(titulo);
        a.setRecebimento(recebimento);
        a.setTipo(tipo);
        a.setNomeOriginal(v.nome());
        a.setTipoConteudo(v.tipoConteudo());
        a.setTamanho(conteudo.length);
        a.setArquivo(armazenamento.salvarEm(ContaReceberAnexoCalculo.pasta(titulo.getId()), conteudo, v.extensao()));
        repository.saveAndFlush(a);
        // se a transação for desfeita depois da gravação do arquivo, remove o arquivo órfão
        String relativo = a.getArquivo();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) armazenamento.excluir(relativo);
            }
        });
        evento(titulo, "ANEXO_ADICIONADO", "Anexo \"" + a.getNomeOriginal() + "\" (" + tipo.name().toLowerCase(java.util.Locale.ROOT) + ", "
                + a.getTamanho() + " bytes) adicionado" + (recebimento == null ? "" : " ao recebimento nº " + recebimento.getId()) + ".");
        return mapper.anexo(a);
    }

    @Transactional(readOnly = true)
    public Download baixar(Long id) {
        ContaReceberAnexo a = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Anexo não encontrado."));
        return new Download(a.getNomeOriginal(), a.getTipoConteudo(), armazenamento.ler(a.getArquivo()));
    }

    @Transactional
    public void excluir(Long id, Collection<String> permissoes) {
        ContaReceberAnexo encontrado = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Anexo não encontrado."));
        if (!permissoes.contains(ContaReceberAnexoCalculo.permissaoExigida(encontrado.getTipo()))) throw new AccessDeniedException("Acesso negado.");
        ContaReceber titulo = contaReceberRepository.findByIdForUpdate(encontrado.getContaReceber().getId())
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
        ContaReceberAnexo a = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Anexo não encontrado."));
        ContaReceberAnexoCalculo.validarRemovivel(titulo.getSituacao());
        String relativo = a.getArquivo();
        repository.delete(a);
        repository.flush();
        evento(titulo, "ANEXO_REMOVIDO", "Anexo \"" + a.getNomeOriginal() + "\" removido.");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                armazenamento.excluir(relativo);
            }
        });
    }

    private byte[] ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new ItemInvalidoException("O arquivo é obrigatório e não pode estar vazio.");
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo enviado", e);
        }
    }

    private void evento(ContaReceber titulo, String nome, String detalhe) {
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        ContaReceberEvento e = new ContaReceberEvento();
        e.setContaReceber(titulo);
        e.setEvento(nome);
        e.setDetalhe(detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe);
        e.setUsuario(usuario);
        e.setDataHora(LocalDateTime.now());
        eventoRepository.save(e);
    }
}
