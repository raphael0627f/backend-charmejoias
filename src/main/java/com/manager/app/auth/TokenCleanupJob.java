package com.manager.app.auth;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// mantém refresh_tokens e access_tokens_revogados pequenas indefinidamente - sem isso, como o
// refresh token é rotacionado a cada uso (single-use), as tabelas cresceriam sem limite
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenRevogadoRepository accessTokenRevogadoRepository;

    @Scheduled(fixedDelayString = "${auth.token-cleanup.fixed-delay}")
    @Transactional
    public void limpar(){
        LocalDateTime agora = LocalDateTime.now();
        refreshTokenRepository.deleteExpiradosOuRevogados(agora);
        accessTokenRevogadoRepository.deleteExpirados(agora);
    }
}
