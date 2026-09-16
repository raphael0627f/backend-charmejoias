package com.manager.app.usuario;

import com.manager.app.shared.exception.ObjetoEmUsoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final PerfilRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final PerfilValidation validation;

    public Page<Perfil> findAll(String busca, ModoBusca modoBusca, Pageable pageable){
        return repository.findAll(comBusca(busca, modoBusca), pageable);
    }

    private Specification<Perfil> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> SpecificationUtils.likeIgnoreCase(cb, root.get("nome"), busca, modoBusca);
    }

    public Perfil findById(Long id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Perfil não encontrado. id: " + id));
    }

    @Transactional
    public Perfil create(Perfil perfil){
        validation.nomeExistenteCreate(perfil.getNome());
        return repository.save(perfil);
    }

    @Transactional
    public Perfil update(Perfil perfil){
        validation.nomeExistenteUpdate(perfil.getNome(), perfil.getId());
        Perfil perfilUpdate = this.findById(perfil.getId());
        perfilUpdate.setNome(perfil.getNome());
        perfilUpdate.setPermissoes(perfil.getPermissoes());
        return perfilUpdate;
    }

    @Transactional
    public void delete(Long id){
        Perfil perfil = this.findById(id);
        if (usuarioRepository.existsByPerfil(perfil)) {
            throw new ObjetoEmUsoException("Perfil está em uso por um ou mais usuários e não pode ser excluído. id: " + id);
        }
        repository.delete(perfil);
    }
}
