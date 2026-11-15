package com.manager.app.financeiro;

import com.manager.app.financeiro.dto.CategoriaReceitaRequestDTO;
import com.manager.app.financeiro.dto.CategoriaReceitaResponseDTO;
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

// categorias de receita em no máximo 2 níveis (grupo > subgrupo); nome único dentro do mesmo pai
@Service
@RequiredArgsConstructor
public class CategoriaReceitaService {

    private static final Comparator<CategoriaReceita> ORDEM = Comparator.comparingInt(CategoriaReceita::getOrdem)
            .thenComparing(c -> BancoService.semAcento(c.getNome()));

    private final CategoriaReceitaRepository repository;
    private final FinanceiroMapper mapper;

    // árvore: grupos com suas subcategorias; com ativo=true um grupo desativado some junto com os subgrupos
    @Transactional(readOnly = true)
    public List<CategoriaReceitaResponseDTO> arvore(Boolean ativo) {
        List<CategoriaReceita> todas = repository.findAll();
        return todas.stream()
                .filter(c -> c.getPai() == null)
                .filter(c -> ativo == null || c.isAtivo() == ativo)
                .sorted(ORDEM)
                .map(grupo -> mapper.categoriaReceita(grupo, todas.stream()
                        .filter(s -> s.getPai() != null && s.getPai().getId().equals(grupo.getId()))
                        .filter(s -> ativo == null || s.isAtivo() == ativo)
                        .sorted(ORDEM)
                        .map(s -> mapper.categoriaReceita(s, null))
                        .toList()))
                .toList();
    }

    // lista plana: grupo seguido dos seus subgrupos
    @Transactional(readOnly = true)
    public List<CategoriaReceitaResponseDTO> plana(Boolean ativo, String busca) {
        List<CategoriaReceita> todas = repository.findAll();
        String termo = busca == null ? "" : BancoService.semAcento(busca.trim());
        return todas.stream()
                .filter(c -> ativo == null || c.isAtivo() == ativo)
                .filter(c -> termo.isEmpty() || BancoService.semAcento(c.getNome()).contains(termo))
                .sorted(Comparator.comparing((CategoriaReceita c) -> c.getPai() == null ? c : c.getPai(), ORDEM)
                        .thenComparing(c -> c.getPai() == null ? 0 : 1)
                        .thenComparing(ORDEM))
                .map(c -> mapper.categoriaReceita(c, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaReceitaResponseDTO obter(Long id) {
        return mapper.categoriaReceita(buscar(id), null);
    }

    @Transactional
    public CategoriaReceitaResponseDTO criar(CategoriaReceitaRequestDTO request) {
        CategoriaReceita categoria = new CategoriaReceita();
        CategoriaReceita pai = resolverPai(request.paiId(), null);
        String nome = nomeValido(request.nome());
        validarNomeUnico(nome, pai, null);
        categoria.setNome(nome);
        categoria.setPai(pai);
        categoria.setOrdem(request.ordem() == null ? 0 : request.ordem());
        categoria.setAtivo(true);
        repository.save(categoria);
        return mapper.categoriaReceita(categoria, null);
    }

    @Transactional
    public CategoriaReceitaResponseDTO atualizar(Long id, CategoriaReceitaRequestDTO request) {
        CategoriaReceita categoria = buscar(id);
        CategoriaReceita pai = resolverPai(request.paiId(), id);
        if (pai != null && repository.existsByPaiId(id)) {
            throw new ItemInvalidoException("Um grupo com subgrupos não pode se tornar subgrupo. São permitidos no máximo dois níveis.");
        }
        String nome = nomeValido(request.nome());
        validarNomeUnico(nome, pai, id);
        categoria.setNome(nome);
        categoria.setPai(pai);
        categoria.setOrdem(request.ordem() == null ? categoria.getOrdem() : request.ordem());
        repository.save(categoria);
        return mapper.categoriaReceita(categoria, null);
    }

    @Transactional
    public CategoriaReceitaResponseDTO ativar(Long id) {
        CategoriaReceita categoria = buscar(id);
        if (categoria.getPai() != null && !categoria.getPai().isAtivo()) {
            throw new ObjetoEmUsoException("Não é possível reativar o subgrupo, pois o grupo \"" + categoria.getPai().getNome() + "\" está desativado. Reative o grupo antes de reativar o subgrupo.");
        }
        categoria.setAtivo(true);
        repository.save(categoria);
        return mapper.categoriaReceita(categoria, null);
    }

    @Transactional
    public CategoriaReceitaResponseDTO inativar(Long id) {
        CategoriaReceita categoria = buscar(id);
        if (repository.existsByPaiIdAndAtivoTrue(id)) {
            throw new ObjetoEmUsoException("Não é possível desativar o grupo \"" + categoria.getNome() + "\", pois ele possui subgrupos ativos. Desative os subgrupos primeiro.");
        }
        categoria.setAtivo(false);
        repository.save(categoria);
        return mapper.categoriaReceita(categoria, null);
    }

    @Transactional
    public void excluir(Long id) {
        CategoriaReceita categoria = buscar(id);
        if (repository.existsByPaiId(id)) {
            throw new ObjetoEmUsoException("Não é possível excluir o grupo \"" + categoria.getNome() + "\", pois ele possui subgrupos. Exclua ou desative os subgrupos primeiro.");
        }
        if (emUso(id)) {
            throw new ObjetoEmUsoException("Não é possível excluir a categoria \"" + categoria.getNome() + "\", pois ela está em uso por títulos a receber. Desative-a.");
        }
        repository.delete(categoria);
        repository.flush();
    }

    // ponto de ligação do uso por título a receber: na Fase 2 passa a consultar conta_receber (categoria_receita_id); nesta fase não há uso
    private boolean emUso(Long id) {
        return false;
    }

    public CategoriaReceita buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Categoria de receita não encontrada."));
    }

    // o pai precisa ser um grupo (sem pai), diferente da própria categoria
    private CategoriaReceita resolverPai(Long paiId, Long propriaId) {
        if (paiId == null) return null;
        if (paiId.equals(propriaId)) throw new ItemInvalidoException("Uma categoria não pode ser pai de si mesma.");
        CategoriaReceita pai = repository.findById(paiId).orElseThrow(() -> new ItemInvalidoException("O grupo informado não foi encontrado."));
        if (pai.getPai() != null) {
            throw new ItemInvalidoException("São permitidos no máximo dois níveis: o pai deve ser um grupo, e não um subgrupo.");
        }
        if (propriaId == null && !pai.isAtivo()) {
            throw new ItemInvalidoException("O grupo \"" + pai.getNome() + "\" está desativado.");
        }
        return pai;
    }

    private String nomeValido(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty()) throw new ItemInvalidoException("O nome é obrigatório.");
        TextoUtils.validarTexto(n, "O nome", 100);
        return n;
    }

    private void validarNomeUnico(String nome, CategoriaReceita pai, Long idAtual) {
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
                    ? "Já existe um grupo de categorias de receita com o nome \"" + nome + "\"."
                    : "Já existe uma categoria de receita com o nome \"" + nome + "\" no grupo \"" + pai.getNome() + "\".");
        }
    }
}
