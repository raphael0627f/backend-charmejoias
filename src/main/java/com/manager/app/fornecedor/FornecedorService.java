package com.manager.app.fornecedor;

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
public class FornecedorService {

    private final FornecedorRepository repository;
    private final ProdutoRepository produtoRepository;

    public Page<Fornecedor> findAll(String busca, ModoBusca modoBusca, Pageable pageable){
        return repository.findAll(comBusca(busca, modoBusca), pageable);
    }

    private Specification<Fornecedor> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca, modoBusca);
    }

    public Fornecedor findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Fornecedor não encontrado. id: " + id));
    }

    @Transactional
    public Fornecedor create(Fornecedor fornecedor){
        normalizarNome(fornecedor);
        if (repository.existsByNomeIgnoreCase(fornecedor.getNome())) {
            throw new ObjetoExistenteException("Fornecedor existente com este nome: " + fornecedor.getNome());
        }
        return repository.save(fornecedor);
    }

    @Transactional
    public Fornecedor update(Fornecedor fornecedor){
        normalizarNome(fornecedor);
        if (repository.existsByNomeIgnoreCaseAndIdNot(fornecedor.getNome(), fornecedor.getId())) {
            throw new ObjetoExistenteException("Fornecedor existente com este nome: " + fornecedor.getNome());
        }
        Fornecedor fornecedorUpdate = repository.findById(fornecedor.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Fornecedor não encontrado. id: " + fornecedor.getId()));
        fornecedorUpdate.setNome(fornecedor.getNome());
        fornecedorUpdate.setAtivo(fornecedor.isAtivo());
        return fornecedorUpdate;
    }

    // espaços nas pontas não contam como diferença de nome
    private void normalizarNome(Fornecedor fornecedor){
        if (fornecedor.getNome() != null) fornecedor.setNome(fornecedor.getNome().trim());
    }

    @Transactional
    public Fornecedor activate(Long id){
        Fornecedor fornecedor = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Fornecedor não encontrado. id: " + id));
        fornecedor.setAtivo(true);
        return fornecedor;
    }

    @Transactional
    public void deactivate(Long id){
        Fornecedor fornecedor = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Fornecedor não encontrado. id: " + id));
        if (produtoRepository.existsByFornecedor(fornecedor)) {
            throw new ObjetoEmUsoException("Fornecedor está em uso e não pode ser desativado");
        }
        fornecedor.setAtivo(false);
    }

}
