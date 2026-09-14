package com.manager.app.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository repository;

    @Override
    public UserDetails loadUserByUsername(String usuario){
        Usuario encontrado = repository.findByUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + usuario));

        List<SimpleGrantedAuthority> authorities = encontrado.getPerfil() != null
                ? encontrado.getPerfil().getPermissoes().stream().map(p -> new SimpleGrantedAuthority(p.name())).toList()
                : List.of();

        return User.builder()
                .username(encontrado.getUsuario())
                .password(encontrado.getSenha())
                .disabled(!encontrado.isAtivo())
                .authorities(authorities)
                .build();
    }
}
