package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CategoriaDespesaRequestDTO;
import com.manager.app.financeiro.dto.CategoriaDespesaResponseDTO;
import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

// categorias de despesa em no máximo 2 níveis (grupo > subgrupo); nome único dentro do mesmo pai
@Service
@RequiredArgsConstructor
public class CategoriaDespesaService {

    private static final Comparator<CategoriaDespesa> ORDEM = Comparator.comparingInt(CategoriaDespesa::getOrdem)
            .thenComparing(c -> BancoService.semAcento(c.getNome()));

    private final CategoriaDespesaRepository repository;
    private final FinanceiroMapper mapper;

    // árvore: grupos com suas subcategorias; com ativo=true um grupo inativo some junto com os subgrupos
    @Transactional(readOnly = true)
    public List<CategoriaDespesaResponseDTO> arvore(Boolean ativo) {
        List<CategoriaDespesa> todas = repository.findAll();
        return todas.stream()
                .filter(c -> c.getPai() == null)
                .filter(c -> ativo == null || c.isAtivo() == ativo)
                .sorted(ORDEM)
                .map(grupo -> mapper.categoria(grupo, todas.stream()
                        .filter(s -> s.getPai() != null && s.getPai().getId().equals(grupo.getId()))
                        .filter(s -> ativo == null || s.isAtivo() == ativo)
                        .sorted(ORDEM)
                        .map(s -> mapper.categoria(s, null))
                        .toList()))
                .toList();
    }

    // lista plana: grupo seguido dos seus subgrupos
    @Transactional(readOnly = true)
    public List<CategoriaDespesaResponseDTO> plana(Boolean ativo, String busca) {
        List<CategoriaDespesa> todas = repository.findAll();
        String termo = busca == null ? "" : BancoService.semAcento(busca.trim());
        return todas.stream()
                .filter(c -> ativo == null || c.isAtivo() == ativo)
                .filter(c -> termo.isEmpty() || BancoService.semAcento(c.getNome()).contains(termo))
                .sorted(Comparator.comparing((CategoriaDespesa c) -> c.getPai() == null ? c : c.getPai(), ORDEM)
                        .thenComparing(c -> c.getPai() == null ? 0 : 1)
                        .thenComparing(ORDEM))
                .map(c -> mapper.categoria(c, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaDespesaResponseDTO obter(Long id) {
        return mapper.categoria(buscar(id), null);
    }

    @Transactional
    public CategoriaDespesaResponseDTO criar(CategoriaDespesaRequestDTO request) {
        CategoriaDespesa categoria = new CategoriaDespesa();
        CategoriaDespesa pai = resolverPai(request.paiId(), null);
        String nome = nomeValido(request.nome());
        validarNomeUnico(nome, pai, null);
        categoria.setNome(nome);
        categoria.setPai(pai);
        categoria.setTipo(request.tipo());
        categoria.setOrdem(request.ordem() == null ? 0 : request.ordem());
        categoria.setAtivo(true);
        repository.save(categoria);
        return mapper.categoria(categoria, null);
    }

    @Transactional
    public CategoriaDespesaResponseDTO atualizar(Long id, CategoriaDespesaRequestDTO request) {
        CategoriaDespesa categoria = buscar(id);
        CategoriaDespesa pai = resolverPai(request.paiId(), id);
        if (pai != null && repository.existsByPaiId(id)) {
            throw new ItemInvalidoException("Um grupo com subgrupos não pode se tornar subgrupo. São permitidos no máximo dois níveis.");
        }
        String nome = nomeValido(request.nome());
        validarNomeUnico(nome, pai, id);
        categoria.setNome(nome);
        categoria.setPai(pai);
        categoria.setTipo(request.tipo());
        categoria.setOrdem(request.ordem() == null ? categoria.getOrdem() : request.ordem());
        repository.save(categoria);
        return mapper.categoria(categoria, null);
    }

    @Transactional
    public CategoriaDespesaResponseDTO ativar(Long id) {
        CategoriaDespesa categoria = buscar(id);
        if (categoria.getPai() != null && !categoria.getPai().isAtivo()) {
            throw new ObjetoEmUsoException("Não é possível ativar o subgrupo, pois o grupo \"" + categoria.getPai().getNome() + "\" está inativo. Ative o grupo primeiro.");
        }
        categoria.setAtivo(true);
        repository.save(categoria);
        return mapper.categoria(categoria, null);
    }

    @Transactional
    public CategoriaDespesaResponseDTO inativar(Long id) {
        CategoriaDespesa categoria = buscar(id);
        if (repository.existsByPaiIdAndAtivoTrue(id)) {
            throw new ObjetoEmUsoException("Não é possível inativar o grupo \"" + categoria.getNome() + "\", pois ele possui subgrupos ativos. Inative os subgrupos primeiro.");
        }
        categoria.setAtivo(false);
        repository.save(categoria);
        return mapper.categoria(categoria, null);
    }

    @Transactional
    public void excluir(Long id) {
        CategoriaDespesa categoria = buscar(id);
        if (repository.existsByPaiId(id)) {
            throw new ObjetoEmUsoException("Não é possível excluir o grupo \"" + categoria.getNome() + "\", pois ele possui subgrupos. Inative-o.");
        }
        repository.delete(categoria);
        repository.flush();
    }

    public CategoriaDespesa buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Categoria de despesa não encontrada."));
    }

    // o pai precisa ser um grupo (sem pai), diferente da própria categoria
    private CategoriaDespesa resolverPai(Long paiId, Long propriaId) {
        if (paiId == null) return null;
        if (paiId.equals(propriaId)) throw new ItemInvalidoException("Uma categoria não pode ser pai de si mesma.");
        CategoriaDespesa pai = repository.findById(paiId).orElseThrow(() -> new ItemInvalidoException("O grupo informado não foi encontrado."));
        if (pai.getPai() != null) {
            throw new ItemInvalidoException("São permitidos no máximo dois níveis: o pai deve ser um grupo, e não um subgrupo.");
        }
        if (propriaId == null && !pai.isAtivo()) {
            throw new ItemInvalidoException("O grupo \"" + pai.getNome() + "\" está inativo.");
        }
        return pai;
    }

    private String nomeValido(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) throw new ItemInvalidoException("O nome é obrigatório.");
        TextoUtils.validarTexto(n, "O nome", 100);
        return n;
    }

    private void validarNomeUnico(String nome, CategoriaDespesa pai, Long idAtual) {
        boolean existe;
        if (pai == null) {
            existe = idAtual == null ? repository.existsByNomeIgnoreCaseAndPaiIsNull(nome)
                    : repository.existsByNomeIgnoreCaseAndPaiIsNullAndIdNot(nome, idAtual);
        } else {
            existe = idAtual == null ? repository.existsByNomeIgnoreCaseAndPaiId(nome, pai.getId())
                    : repository.existsByNomeIgnoreCaseAndPaiIdAndIdNot(nome, pai.getId(), idAtual);
        }
        if (existe) {
            throw new ObjetoExistenteException(pai == null
                    ? "Já existe um grupo de categorias com o nome \"" + nome + "\"."
                    : "Já existe uma categoria com o nome \"" + nome + "\" no grupo \"" + pai.getNome() + "\".");
        }
    }
}
