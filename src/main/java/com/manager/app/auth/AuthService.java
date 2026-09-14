package com.manager.app.auth;

import com.manager.app.auth.dto.LoginResponseDTO;
import com.manager.app.auth.dto.MeResponseDTO;
import com.manager.app.security.AuthenticatedTokenDetails;
import com.manager.app.security.JwtService;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.usuario.Usuario;
import com.manager.app.usuario.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRevogadoRepository accessTokenRevogadoRepository;
    private final JwtService jwtService;

    @Value("${jwt.refresh-expiration-seconds}")
    private long refreshExpirationSeconds;

    @Transactional
    public LoginResponseDTO login(String usuario, String senha){
        // dispara UsuarioDetailsService + PasswordEncoder por baixo dos panos; lança
        // BadCredentialsException/DisabledException em caso de falha (tratado no RestExceptionHandler)
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(usuario, senha));

        Usuario usuarioEntidade = usuarioRepository.findByUsuario(usuario)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Usuário não encontrado: " + usuario));

        return gerarResposta(usuarioEntidade);
    }

    @Transactional
    public LoginResponseDTO refresh(String refreshTokenValue){
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new ItemInvalidoException("Refresh token inválido"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ItemInvalidoException("Refresh token inválido ou expirado");
        }

        // rotação: o refresh token usado nunca pode ser reaproveitado (single-use)
        refreshToken.setRevoked(true);

        return gerarResposta(refreshToken.getUsuario());
    }

    @Transactional
    public void logout(String refreshTokenValue){
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(rt -> rt.setRevoked(true));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof AuthenticatedTokenDetails detalhes) {
            AccessTokenRevogado revogado = new AccessTokenRevogado(
                    java.util.UUID.fromString(detalhes.jti()),
                    LocalDateTime.ofInstant(detalhes.expiresAt(), ZoneId.systemDefault()),
                    LocalDateTime.now());
            accessTokenRevogadoRepository.save(revogado);
        }
    }

    public MeResponseDTO me(String usuario){
        Usuario usuarioEntidade = usuarioRepository.findByUsuario(usuario)
                .orElseThrow(() -> new ObjetoNaoEncontradoException("Usuário não encontrado: " + usuario));

        String perfil = usuarioEntidade.getPerfil() != null ? usuarioEntidade.getPerfil().getNome() : null;
        List<String> permissoes = usuarioEntidade.getPerfil() != null
                ? usuarioEntidade.getPerfil().getPermissoes().stream().map(Enum::name).toList()
                : List.of();

        return new MeResponseDTO(usuarioEntidade.getUsuario(), perfil, permissoes);
    }

    private LoginResponseDTO gerarResposta(Usuario usuario){
        JwtService.GeneratedToken accessToken = jwtService.gerarAccessToken(usuario);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(jwtService.gerarRefreshTokenValue());
        refreshToken.setUsuario(usuario);
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshExpirationSeconds));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        String perfil = usuario.getPerfil() != null ? usuario.getPerfil().getNome() : null;
        List<String> permissoes = usuario.getPerfil() != null
                ? usuario.getPerfil().getPermissoes().stream().map(Enum::name).toList()
                : List.of();

        long expiresInSeconds = Instant.now().until(accessToken.expiresAt(), java.time.temporal.ChronoUnit.SECONDS);

        return new LoginResponseDTO(accessToken.token(), refreshToken.getToken(), expiresInSeconds, usuario.getUsuario(), perfil, permissoes);
    }
}
