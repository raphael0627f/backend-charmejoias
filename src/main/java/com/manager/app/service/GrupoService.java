package com.manager.app.service;

import com.manager.app.entity.Grupo;
import com.manager.app.infra.exception.ObjetoEmUsoException;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.repository.ClienteRepository;
import com.manager.app.repository.GrupoRepository;
import com.manager.app.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GrupoService {

    private final GrupoRepository repository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;

    public List<Grupo> findAll(){
        return repository.findAll();
    }

    public Grupo findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Grupo não encontrado. id: " + id));
    }

    @Transactional
    public Grupo create(Grupo grupo){
        return repository.save(grupo);
    }

    @Transactional
    public Grupo update(Grupo grupo){
        Grupo grupoUpdate = repository.findById(grupo.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Grupo não encontrado. id: " + grupo.getId()));
        grupoUpdate.setNome(grupo.getNome());
        grupoUpdate.setAtivo(grupo.isAtivo());
        return grupoUpdate;
    }

    @Transactional
    public Grupo activate(Long id){
        Grupo grupo = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Grupo não encontrado. id: " + id));
        grupo.setAtivo(true);
        return grupo;
    }

    @Transactional
    public void deactivate(Long id){
        Grupo grupo = repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Grupo não encontrado. id: " + id));
        if (clienteRepository.existsByGrupos(grupo) || produtoRepository.existsByGrupos(grupo)) {
            throw new ObjetoEmUsoException("Grupo está em uso e não pode ser desativado");
        }
        grupo.setAtivo(false);
    }
}
