package com.manager.app.security;

import java.time.Instant;

// carregado em Authentication.setDetails(...) pelo JwtAuthenticationFilter, pra AuthService.logout
// conseguir pegar o jti/expiração do access token atual sem precisar reparsear o token
public record AuthenticatedTokenDetails(String jti, Instant expiresAt) {}
