package com.manager.app.categoria;

import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoExistenteException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.produto.ProdutoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository repository;
    private final ProdutoRepository produtoRepository;

    public Page<Categoria> findAll(String busca, ModoBusca modoBusca, Pageable pageable){
        return repository.findAll(comBusca(busca, modoBusca), pageable);
    }

    private Specification<Categoria> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca, modoBusca);
    }

    public Categoria findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Categoria não encontrada. id: " + id));
    }

    @Transactional
    public Categoria create(Categoria categoria){
        normalizarNome(categoria);
        if (repository.existsByNomeIgnoreCase(categoria.getNome())) {
            throw new ObjetoExistenteException("Categoria existente com este nome: " + categoria.getNome());
        }
        return repository.save(categoria);
    }

    @Transactional
    public Categoria update(Categoria categoria){
        normalizarNome(categoria);
        if (repository.existsByNomeIgnoreCaseAndIdNot(categoria.getNome(), categoria.getId())) {
            throw new ObjetoExistenteException("Categoria existente com este nome: " + categoria.getNome());
        }
        Categoria categoriaUpdate = repository.findById(categoria.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Categoria não encontrada. id: " + categoria.getId()));
        categoriaUpdate.setNome(categoria.getNome());
        categoriaUpdate.setAtivo(categoria.isAtivo());
        return categoriaUpdate;
    }

    // espaços nas pontas não contam como diferença de nome
    private void normalizarNome(Categoria categoria){
        if (categoria.getNome() != null) categoria.setNome(categoria.getNome().trim());
    }

    @Transactional
    public Categoria activate(Long id){
        Categoria categoria = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Categoria não encontrada. id: " + id));
        categoria.setAtivo(true);
        return categoria;
    }

    @Transactional
    public void deactivate(Long id){
        Categoria categoria = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Categoria não encontrada. id: " + id));
        if (produtoRepository.existsByCategoria(categoria)) {
            throw new ObjetoEmUsoException("Categoria está em uso e não pode ser desativada");
        }
        categoria.setAtivo(false);
    }
}
