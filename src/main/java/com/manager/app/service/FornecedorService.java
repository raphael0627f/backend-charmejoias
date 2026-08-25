package com.manager.app.service;

import com.manager.app.entity.Fornecedor;
import com.manager.app.infra.exception.ObjetoEmUsoException;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.specification.SpecificationUtils;
import com.manager.app.repository.FornecedorRepository;
import com.manager.app.repository.ProdutoRepository;
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

    public Page<Fornecedor> findAll(String busca, Pageable pageable){
        return repository.findAll(comBusca(busca), pageable);
    }

    private Specification<Fornecedor> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca);
    }

    public Fornecedor findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Fornecedor não encontrado. id: " + id));
    }

    @Transactional
    public Fornecedor create(Fornecedor fornecedor){
        return repository.save(fornecedor);
    }

    @Transactional
    public Fornecedor update(Fornecedor fornecedor){
        Fornecedor fornecedorUpdate = repository.findById(fornecedor.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Fornecedor não encontrado. id: " + fornecedor.getId()));
        fornecedorUpdate.setNome(fornecedor.getNome());
        fornecedorUpdate.setAtivo(fornecedor.isAtivo());
        return fornecedorUpdate;
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
