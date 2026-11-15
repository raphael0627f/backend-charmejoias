package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.AnexoDTO;
import com.manager.app.security.UsuarioAuditorAware;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
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

// anexos do título (documento, boleto) e comprovantes de pagamento. O título é bloqueado (SELECT FOR UPDATE) para que o limite de 10 anexos
// valha mesmo com envios simultâneos.
@Service
@RequiredArgsConstructor
public class ContaPagarAnexoService {

    private final ContaPagarAnexoRepository repository;
    private final ContaPagarRepository contaPagarRepository;
    private final ContaPagarPagamentoRepository pagamentoRepository;
    private final ContaPagarEventoRepository eventoRepository;
    private final ContaPagarAnexoArmazenamento armazenamento;
    private final ContaPagarMapper mapper;
    private final UsuarioAuditorAware auditor;

    public record Download(String nome, String tipoConteudo, byte[] conteudo) {
    }

    @Transactional
    public AnexoDTO anexarAoTitulo(Long tituloId, TipoAnexo tipo, MultipartFile arquivo) {
        if (tipo == null) throw new ItemInvalidoException("Informe o tipo do anexo: DOCUMENTO ou BOLETO.");
        if (tipo == TipoAnexo.COMPROVANTE) throw new ItemInvalidoException("O comprovante deve ser anexado diretamente ao pagamento.");
        AnexoValidacao.Validado v = AnexoValidacao.validar(ler(arquivo), arquivo.getOriginalFilename());
        ContaPagar titulo = contaPagarRepository.findByIdForUpdate(tituloId).orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
        return gravar(titulo, null, tipo, v, ler(arquivo));
    }

    @Transactional
    public AnexoDTO anexarComprovante(Long pagamentoId, MultipartFile arquivo) {
        AnexoValidacao.Validado v = AnexoValidacao.validar(ler(arquivo), arquivo.getOriginalFilename());
        Long tituloId = pagamentoRepository.findContaPagarIdById(pagamentoId).orElseThrow(() -> new ObjetoNaoEncontradoException("Pagamento não encontrado."));
        ContaPagar titulo = contaPagarRepository.findByIdForUpdate(tituloId).orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
        ContaPagarPagamento pagamento = pagamentoRepository.findById(pagamentoId).orElseThrow(() -> new ObjetoNaoEncontradoException("Pagamento não encontrado."));
        if (pagamento.isEstornada()) throw new ItemInvalidoException("O pagamento está estornado e não aceita novos comprovantes.");
        return gravar(titulo, pagamento, TipoAnexo.COMPROVANTE, v, ler(arquivo));
    }

    private AnexoDTO gravar(ContaPagar titulo, ContaPagarPagamento pagamento, TipoAnexo tipo, AnexoValidacao.Validado v, byte[] conteudo) {
        if (titulo.getSituacao() == SituacaoContaPagar.CANCELADO) throw new ItemInvalidoException("O título cancelado não aceita novos anexos.");
        if (repository.countByContaPagarId(titulo.getId()) >= AnexoValidacao.MAXIMO_POR_TITULO) {
            throw new ObjetoEmUsoException("O título já possui o número máximo de " + AnexoValidacao.MAXIMO_POR_TITULO + " anexos.");
        }
        ContaPagarAnexo a = new ContaPagarAnexo();
        a.setContaPagar(titulo);
        a.setPagamento(pagamento);
        a.setTipo(tipo);
        a.setNomeOriginal(v.nome());
        a.setTipoConteudo(v.tipoConteudo());
        a.setTamanho(conteudo.length);
        a.setArquivo(armazenamento.salvar(titulo.getId(), conteudo, v.extensao()));
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
                + a.getTamanho() + " bytes) adicionado" + (pagamento == null ? "" : " ao pagamento nº " + pagamento.getId()) + ".");
        return mapper.anexo(a);
    }

    @Transactional(readOnly = true)
    public Download baixar(Long id) {
        ContaPagarAnexo a = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Anexo não encontrado."));
        return new Download(a.getNomeOriginal(), a.getTipoConteudo(), armazenamento.ler(a.getArquivo()));
    }

    // comprovante exige a permissão de pagamento; documento e boleto, a de gerência
    @Transactional
    public void excluir(Long id, Collection<String> permissoes) {
        ContaPagarAnexo encontrado = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Anexo não encontrado."));
        String exigida = encontrado.getTipo() == TipoAnexo.COMPROVANTE ? "CONTA_PAGAR_PAGAR" : "CONTA_PAGAR_GERENCIAR";
        if (!permissoes.contains(exigida)) throw new AccessDeniedException("Acesso negado.");
        ContaPagar titulo = contaPagarRepository.findByIdForUpdate(encontrado.getContaPagar().getId())
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Título não encontrado."));
        ContaPagarAnexo a = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Anexo não encontrado."));
        if (titulo.getSituacao() == SituacaoContaPagar.CANCELADO) throw new ItemInvalidoException("O título cancelado não permite remover anexos.");
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

    private void evento(ContaPagar titulo, String nome, String detalhe) {
        Usuario usuario = auditor.getCurrentAuditor().orElse(null);
        ContaPagarEvento e = new ContaPagarEvento();
        e.setContaPagar(titulo);
        e.setEvento(nome);
        e.setDetalhe(detalhe.length() > 2000 ? detalhe.substring(0, 2000) : detalhe);
        e.setUsuario(usuario);
        e.setDataHora(LocalDateTime.now());
        eventoRepository.save(e);
    }
}
