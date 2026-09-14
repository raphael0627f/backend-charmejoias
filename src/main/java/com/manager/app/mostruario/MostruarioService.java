package com.manager.app.mostruario;

import com.manager.app.consignacao.ConsignacaoRepository;
import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MostruarioService {

    private final MostruarioRepository repository;
    private final ConsignacaoRepository consignacaoRepository;

    public Page<Mostruario> findAll(String busca, Pageable pageable){
        return repository.findAll(comBusca(busca), pageable);
    }

    private Specification<Mostruario> comBusca(String busca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("descricao"), busca);
    }

    public Mostruario findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Mostruário não encontrado. id: " + id));
    }

    @Transactional
    public Mostruario create(Mostruario mostruario){
        return repository.save(mostruario);
    }

    @Transactional
    public Mostruario update(Mostruario mostruario){
        Mostruario mostruarioUpdate = this.findById(mostruario.getId());
        mostruarioUpdate.setDescricao(mostruario.getDescricao());
        return mostruarioUpdate;
    }

    @Transactional
    public void delete(Long id){
        Mostruario mostruario = this.findById(id);
        if (consignacaoRepository.existsByMostruario(mostruario)) {
            throw new ObjetoEmUsoException("Mostruário está em uso por uma ou mais Consignações e não pode ser excluído. id: " + id);
        }
        repository.delete(mostruario);
    }
}
