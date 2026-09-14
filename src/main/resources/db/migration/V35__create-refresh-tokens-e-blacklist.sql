-- =========================
-- TABELA: refresh_tokens
-- =========================
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    usuario_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP,
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- =========================
-- TABELA: access_tokens_revogados
-- =========================
-- blacklist de access tokens revogados no logout, antes de expirarem sozinhos (jti = claim "jti"
-- do JWT, não um id gerado). Limpa periodicamente via TokenCleanupJob depois que expira_em passa,
-- já que depois disso o próprio token expiraria de qualquer forma
CREATE TABLE access_tokens_revogados (
    jti UUID PRIMARY KEY,
    expira_em TIMESTAMP NOT NULL,
    revogado_em TIMESTAMP NOT NULL
);
