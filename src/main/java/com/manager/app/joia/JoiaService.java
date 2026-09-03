package com.manager.app.joia;

import com.manager.app.joia.dto.JoiaRelacoesDTO;
import com.manager.app.grupo.Grupo;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.grupo.GrupoValidation;
import com.manager.app.grupo.GrupoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import com.manager.app.categoria.CategoriaService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JoiaService {

    private final JoiaRepository repository;
    private final GrupoRepository grupoRepository;
    private final CategoriaService categoriaService;
    private final JoiaValidation validation;
    private final GrupoValidation grupoValidation;

    public Page<Joia> findAll(String busca, Boolean ativo, Long categoriaId, Pageable pageable){
        Specification<Joia> spec = comBusca(busca);

        if (ativo != null) {
            Specification<Joia> ativoSpec = (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
            spec = spec == null ? ativoSpec : spec.and(ativoSpec);
        }

        if (categoriaId != null) {
            Specification<Joia> categoriaSpec = (root, query, cb) -> cb.equal(root.get("categoria").get("id"), categoriaId);
            spec = spec == null ? categoriaSpec : spec.and(categoriaSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<Joia> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> cb.or(
                SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("codigo"), busca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("referencia"), busca)
        );
    }

    public Joia findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Joia não encontrada. id: " + id));
    }

    public Joia findByIdAndAtivo(UUID id){
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Joia não encontrada ou inativa. id: " + id));
    }

    public Joia findByCodigo(String codigo){
        return repository.findByCodigoAndAtivoTrue(codigo).orElseThrow(() -> new ObjetoNaoEncontradoException("Joia não encontrada. codigo: " + codigo));
    }

    @Transactional
    public Joia create(Joia joia, JoiaRelacoesDTO relacoes){
        validation.codigoExistenteCreate(joia.getCodigo());

        joia.setCategoria(categoriaService.findById(relacoes.getCategoriaId()));

        if(!grupoValidation.gruposEmpty(relacoes.getGruposIds())){
            List<Grupo> grupos = grupoRepository.findAllById(relacoes.getGruposIds());
            if (grupos.size() != relacoes.getGruposIds().size()) {
                throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
            }
            joia.setGrupos(grupos);
        }
        return repository.save(joia);
    }

    @Transactional
    public Joia update(Joia joia, JoiaRelacoesDTO relacoes){
        validation.codigoExistenteUpdate(joia.getCodigo(), joia.getId());
        Joia joiaUpdate = repository.findById(joia.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Joia não encontrada. id: " + joia.getId()));

        joiaUpdate.setCodigo(joia.getCodigo());
        joiaUpdate.setReferencia(joia.getReferencia());
        joiaUpdate.setNome(joia.getNome());
        joiaUpdate.setPrecoVenda(joia.getPrecoVenda());
        joiaUpdate.setAtivo(joia.isAtivo());
        joiaUpdate.setObservacoes(joia.getObservacoes());
        joiaUpdate.setCategoria(categoriaService.findById(relacoes.getCategoriaId()));

        if(!grupoValidation.gruposEmpty(relacoes.getGruposIds())){
            List<Grupo> grupos = grupoRepository.findAllById(relacoes.getGruposIds());
            if (grupos.size() != relacoes.getGruposIds().size()) {
                throw new ObjetoNaoEncontradoException("Um ou mais grupos não encontrados");
            }
            joiaUpdate.setGrupos(grupos);
        }
        return joiaUpdate;
    }

    @Transactional
    public Joia activate(UUID id){
        Joia joia = this.findById(id);
        joia.setAtivo(true);
        return joia;
    }

    @Transactional
    public void deactivate(UUID id){
        Joia joia = this.findById(id);
        joia.setAtivo(false);
    }
}
