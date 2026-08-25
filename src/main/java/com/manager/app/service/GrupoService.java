package com.manager.app.service;

import com.manager.app.entity.Grupo;
import com.manager.app.infra.exception.ObjetoEmUsoException;
import com.manager.app.infra.exception.ObjetoNaoEncontradoException;
import com.manager.app.infra.specification.SpecificationUtils;
import com.manager.app.repository.ClienteRepository;
import com.manager.app.repository.GrupoRepository;
import com.manager.app.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GrupoService {

    private final GrupoRepository repository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;

    public Page<Grupo> findAll(String busca, Pageable pageable){
        return repository.findAll(comBusca(busca), pageable);
    }

    private Specification<Grupo> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca);
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
