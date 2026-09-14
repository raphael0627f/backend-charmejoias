package com.manager.app.security;

import com.manager.app.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration-seconds}") long expirationSeconds){
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret não configurado - defina a variável de ambiente JWT_SECRET (mínimo 32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public GeneratedToken gerarAccessToken(Usuario usuario){
        String jti = UUID.randomUUID().toString();
        Instant agora = Instant.now();
        Instant expiracao = agora.plusSeconds(expirationSeconds);

        String perfil = usuario.getPerfil() != null ? usuario.getPerfil().getNome() : null;
        List<String> permissoes = usuario.getPerfil() != null
                ? usuario.getPerfil().getPermissoes().stream().map(Enum::name).toList()
                : List.of();

        String token = Jwts.builder()
                .id(jti)
                .subject(usuario.getUsuario())
                .claim("perfil", perfil)
                .claim("permissoes", permissoes)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiracao))
                .signWith(key)
                .compact();

        return new GeneratedToken(token, jti, expiracao);
    }

    public Claims validarEExtrairClaims(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    // valor opaco de alta entropia pro refresh token - não é um JWT, só uma string aleatória
    // persistida no banco (RefreshToken.token)
    public String gerarRefreshTokenValue(){
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record GeneratedToken(String token, String jti, Instant expiresAt) {}
}
