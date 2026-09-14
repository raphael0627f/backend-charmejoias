package com.manager.app.security;

import com.manager.app.auth.AccessTokenRevogadoRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final AccessTokenRevogadoRepository accessTokenRevogadoRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIXO_BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIXO_BEARER.length());

        try {
            Claims claims = jwtService.validarEExtrairClaims(token);
            UUID jti = UUID.fromString(claims.getId());

            if (accessTokenRevogadoRepository.existsByJti(jti)) {
                filterChain.doFilter(request, response);
                return;
            }

            String usuario = claims.getSubject();
            @SuppressWarnings("unchecked")
            List<String> permissoes = claims.get("permissoes", List.class);
            List<GrantedAuthority> authorities = (permissoes != null ? permissoes : List.<String>of()).stream()
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();

            var authToken = new UsernamePasswordAuthenticationToken(usuario, null, authorities);
            authToken.setDetails(new AuthenticatedTokenDetails(claims.getId(), claims.getExpiration().toInstant()));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (JwtException | IllegalArgumentException e) {
            // token inválido/expirado/malformado - segue sem autenticar; quem exigir
            // autenticação vai receber 401 do AuthenticationEntryPoint
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
