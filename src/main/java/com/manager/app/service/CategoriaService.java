package com.manager.app.service;

import com.manager.app.entity.Categoria;
import com.manager.app.infra.exception.ObjetoEmUsoException;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.repository.CategoriaRepository;
import com.manager.app.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository repository;
    private final ProdutoRepository produtoRepository;

    public List<Categoria> findAll(){
        return repository.findAll();
    }

    public Categoria findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Categoria não encontrada. id: " + id));
    }

    @Transactional
    public Categoria create(Categoria categoria){
        return repository.save(categoria);
    }

    @Transactional
    public Categoria update(Categoria categoria){
        Categoria categoriaUpdate = repository.findById(categoria.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Categoria não encontrada. id: " + categoria.getId()));
        categoriaUpdate.setNome(categoria.getNome());
        categoriaUpdate.setAtivo(categoria.isAtivo());
        return categoriaUpdate;
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
