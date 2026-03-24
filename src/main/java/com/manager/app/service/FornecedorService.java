package com.manager.app.service;

import com.manager.app.entity.Fornecedor;
import com.manager.app.infra.exception.ObjetoEmUsoException;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.repository.FornecedorRepository;
import com.manager.app.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FornecedorService {

    private final FornecedorRepository repository;
    private final ProdutoRepository produtoRepository;

    public List<Fornecedor> findAll(){
        return repository.findAll();
    }

    public Fornecedor findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Fornecedor não encontrado. id: " + id));
    }

    @Transactional
    public Fornecedor create(Fornecedor fornecedor){
        return repository.save(fornecedor);
    }

    public Fornecedor update(Fornecedor fornecedor){
        Fornecedor fornecedorUpdate = repository.findById(fornecedor.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Fornecedor não encontrado. id: " + fornecedor.getId()));
        fornecedorUpdate.setNome(fornecedor.getNome());
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
