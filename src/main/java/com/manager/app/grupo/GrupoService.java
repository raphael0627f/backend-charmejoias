package com.manager.app.grupo;

import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import com.manager.app.cliente.ClienteRepository;
import com.manager.app.joia.JoiaRepository;
import com.manager.app.produto.ProdutoRepository;
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
    private final JoiaRepository joiaRepository;

    public Page<Grupo> findAll(String busca, ModoBusca modoBusca, TipoGrupo tipo, Pageable pageable){
        Specification<Grupo> spec = comBusca(busca, modoBusca);

        if (tipo != null) {
            Specification<Grupo> tipoSpec = (root, query, cb) -> cb.equal(root.get("tipo"), tipo);
            spec = spec == null ? tipoSpec : spec.and(tipoSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<Grupo> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca, modoBusca);
    }

    public Grupo findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Grupo não encontrado. id: " + id));
    }

    @Transactional
    public Grupo create(Grupo grupo){
        return repository.save(grupo);
    }

    private boolean emUso(Grupo grupo){
        return clienteRepository.existsByGrupos(grupo)
                || produtoRepository.existsByGrupos(grupo)
                || joiaRepository.existsByGrupos(grupo);
    }

    @Transactional
    public Grupo update(Grupo grupo){
        Grupo grupoUpdate = repository.findById(grupo.getId()).orElseThrow(() -> new ObjetoNaoEncontradoException("Grupo não encontrado. id: " + grupo.getId()));

        if (grupoUpdate.getTipo() != grupo.getTipo() && emUso(grupoUpdate)) {
            throw new ObjetoEmUsoException("Grupo está em uso e não pode mudar de tipo");
        }

        grupoUpdate.setNome(grupo.getNome());
        grupoUpdate.setAtivo(grupo.isAtivo());
        grupoUpdate.setTipo(grupo.getTipo());
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
        if (emUso(grupo)) {
            throw new ObjetoEmUsoException("Grupo está em uso e não pode ser desativado");
        }
        grupo.setAtivo(false);
    }
}
