package com.manager.app.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AccessTokenRevogadoRepository extends JpaRepository<AccessTokenRevogado, UUID> {
    boolean existsByJti(UUID jti);

    @Modifying
    @Query("delete from AccessTokenRevogado a where a.expiraEm < :agora")
    void deleteExpirados(LocalDateTime agora);
}
