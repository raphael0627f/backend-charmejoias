package com.manager.app.agenda;

import com.manager.app.cidade.Cidade;
import com.manager.app.cidade.CidadeService;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgendaRotaService {

    private final AgendaRotaRepository repository;
    private final AgendaRotaValidation validation;
    private final CidadeService cidadeService;

    public Page<AgendaRota> findAll(String nome, Pageable pageable){
        Specification<AgendaRota> spec = null;
        if (nome != null && !nome.isBlank()) {
            String termo = nome.trim();
            spec = SpecificationUtils.and(spec, (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), termo));
        }
        return repository.findAll(spec, pageable);
    }

    public AgendaRota findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Rota da agenda não encontrada. id: " + id));
    }

    @Transactional
    public AgendaRota create(AgendaRota rota, List<Long> cidadeIds){
        rota.setId(null);
        normalizar(rota);
        validation.nomeExistenteCreate(rota.getNome());
        validation.cidadesObrigatorias(cidadeIds);
        rota.setCidades(cidadeService.findAllOrdenado(cidadeIds));
        return repository.save(rota);
    }

    @Transactional
    public AgendaRota update(AgendaRota rota, List<Long> cidadeIds){
        if (rota.getId() == null) {
            throw new ItemInvalidoException("Id da rota é obrigatório");
        }
        AgendaRota rotaUpdate = this.findById(rota.getId());
        normalizar(rota);
        validation.nomeExistenteUpdate(rota.getNome(), rotaUpdate.getId());
        validation.cidadesObrigatorias(cidadeIds);
        List<Cidade> novas = cidadeService.findAllOrdenado(cidadeIds);
        rotaUpdate.setNome(rota.getNome());
        // limpa e grava antes de repor: a ordem muda e (rota_id, cidade_id) é único
        rotaUpdate.getCidades().clear();
        repository.flush();
        rotaUpdate.getCidades().addAll(novas);
        return rotaUpdate;
    }

    @Transactional
    public void delete(Long id){
        AgendaRota rota = this.findById(id);
        validation.podeExcluir(id);
        repository.delete(rota);
    }

    private void normalizar(AgendaRota rota){
        if (rota.getNome() != null) {
            rota.setNome(rota.getNome().trim());
        }
    }
}
