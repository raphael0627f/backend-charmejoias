package com.manager.app.usuario;

import com.manager.app.endereco.Endereco;
import com.manager.app.endereco.EnderecoService;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.ModoBusca;
import com.manager.app.shared.specification.SpecificationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PerfilRepository perfilRepository;
    private final EnderecoService enderecoService;
    private final UsuarioValidation validation;
    private final PasswordEncoder passwordEncoder;

    public Page<Usuario> findAll(String busca, ModoBusca modoBusca, Boolean ativo, Pageable pageable){
        Specification<Usuario> spec = comBusca(busca, modoBusca);

        if (ativo != null) {
            Specification<Usuario> ativoSpec = (root, query, cb) -> cb.equal(root.get("ativo"), ativo);
            spec = SpecificationUtils.and(spec, ativoSpec);
        }

        return repository.findAll(spec, pageable);
    }

    private Specification<Usuario> comBusca(String busca, ModoBusca modoBusca){
        if (busca == null || busca.isBlank()) return null;
        return (root, query, cb) -> cb.or(
                SpecificationUtils.likeIgnoreCase(cb, root.get("usuario"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("primeiroNome"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("sobrenome"), busca, modoBusca),
                SpecificationUtils.likeIgnoreCase(cb, root.get("email"), busca, modoBusca)
        );
    }

    public Usuario findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new ObjetoNaoEncontradoException("Usuário não encontrado. id: " + id));
    }

    private Perfil resolverPerfil(Long perfilId){
        if (perfilId == null) return null;
        return perfilRepository.findById(perfilId)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Perfil não encontrado. id: " + perfilId));
    }

    @Transactional
    public Usuario create(Usuario usuario, String senha, Long perfilId){
        validation.usuarioExistenteCreate(usuario.getUsuario());
        if (senha == null || senha.isBlank()) {
            throw new ItemInvalidoException("Senha é obrigatória");
        }

        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setPerfil(resolverPerfil(perfilId));

        if (usuario.getEndereco() != null) {
            enderecoService.enriquecerIbge(usuario.getEndereco());
        }

        return repository.save(usuario);
    }

    @Transactional
    public Usuario update(Usuario usuario, String senha, Long perfilId){
        validation.usuarioExistenteUpdate(usuario.getUsuario(), usuario.getId());
        Usuario usuarioUpdate = this.findById(usuario.getId());

        usuarioUpdate.setUsuario(usuario.getUsuario());
        usuarioUpdate.setPrimeiroNome(usuario.getPrimeiroNome());
        usuarioUpdate.setSobrenome(usuario.getSobrenome());
        usuarioUpdate.setCpf(usuario.getCpf());
        usuarioUpdate.setDocumento(usuario.getDocumento());
        usuarioUpdate.setCelular(usuario.getCelular());
        usuarioUpdate.setEmail(usuario.getEmail());
        usuarioUpdate.setObservacao(usuario.getObservacao());
        usuarioUpdate.setAtivo(usuario.isAtivo());
        usuarioUpdate.setPerfil(resolverPerfil(perfilId));

        if (senha != null && !senha.isBlank()) {
            usuarioUpdate.setSenha(passwordEncoder.encode(senha));
        }

        sincronizarEndereco(usuarioUpdate, usuario.getEndereco());

        return usuarioUpdate;
    }

    // Usuario tem só um endereço (OneToOne), diferente da lista de Cliente - aqui é só
    // atualizar/criar/remover o único endereço conforme o que veio na requisição
    private void sincronizarEndereco(Usuario usuarioUpdate, Endereco enderecoRequest){
        if (enderecoRequest == null) {
            usuarioUpdate.setEndereco(null);
            return;
        }

        Endereco enderecoAtual = usuarioUpdate.getEndereco();
        if (enderecoAtual != null) {
            enderecoAtual.setLogradouro(enderecoRequest.getLogradouro());
            enderecoAtual.setNumero(enderecoRequest.getNumero());
            enderecoAtual.setComplemento(enderecoRequest.getComplemento());
            enderecoAtual.setBairro(enderecoRequest.getBairro());
            enderecoAtual.setMunicipio(enderecoRequest.getMunicipio());
            enderecoAtual.setUf(enderecoRequest.getUf());
            enderecoAtual.setCep(enderecoRequest.getCep());
            enderecoAtual.setIbge(enderecoRequest.getIbge());
            enderecoService.enriquecerIbge(enderecoAtual);
        } else {
            enderecoService.enriquecerIbge(enderecoRequest);
            usuarioUpdate.setEndereco(enderecoRequest);
        }
    }

    @Transactional
    public Usuario activate(UUID id){
        Usuario usuario = this.findById(id);
        usuario.setAtivo(true);
        return usuario;
    }

    @Transactional
    public void deactivate(UUID id){
        Usuario usuario = this.findById(id);
        usuario.setAtivo(false);
    }
}
