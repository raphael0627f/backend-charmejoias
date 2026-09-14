package com.manager.app.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// blacklist de access tokens revogados no logout antes de expirar sozinhos - o "id" aqui é o
// próprio claim "jti" do JWT, não um id gerado
@Entity
@Table(name = "access_tokens_revogados")
@EqualsAndHashCode(of = "jti")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccessTokenRevogado {
    @Id
    @Column(name = "jti")
    private UUID jti;

    private LocalDateTime expiraEm;
    private LocalDateTime revogadoEm;
}
