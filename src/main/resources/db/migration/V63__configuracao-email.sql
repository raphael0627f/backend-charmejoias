-- Configuracao do e-mail de envio (SMTP), feita pelo usuario na tela de Sistema: UMA linha (singleton, id = 1).
-- Aditiva: tabela nova e uma permissao nova para o perfil ADMIN. A senha fica criptografada (AES-GCM); a API nunca a devolve.
CREATE TABLE configuracao_email (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    host VARCHAR(255),
    porta INTEGER,
    usuario VARCHAR(255),
    senha_criptografada TEXT,
    remetente VARCHAR(255),
    nome_remetente VARCHAR(100),
    starttls BOOLEAN NOT NULL DEFAULT TRUE,
    ssl BOOLEAN NOT NULL DEFAULT FALSE,
    timeout_ms INTEGER NOT NULL DEFAULT 30000,
    ativo BOOLEAN NOT NULL DEFAULT FALSE,
    atualizado_em TIMESTAMP,
    atualizado_por_id UUID REFERENCES usuarios (id)
);

-- so o perfil ADMIN (mesmo padrao do backup): a configuracao contem credenciais de e-mail
INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'CONFIGURACAO_EMAIL_GERENCIAR'
FROM perfis
WHERE perfis.nome = 'ADMIN';
