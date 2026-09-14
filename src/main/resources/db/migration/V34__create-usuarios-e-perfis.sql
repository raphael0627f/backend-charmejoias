-- =========================
-- TABELA: perfis
-- =========================
CREATE TABLE perfis (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_perfis_nome UNIQUE (nome)
);

-- =========================
-- TABELA: perfis_permissoes
-- =========================
CREATE TABLE perfis_permissoes (
    perfil_id BIGINT NOT NULL,
    permissao VARCHAR(50) NOT NULL,
    PRIMARY KEY (perfil_id, permissao),
    CONSTRAINT fk_perfis_permissoes_perfil FOREIGN KEY (perfil_id) REFERENCES perfis(id)
);

-- =========================
-- TABELA: usuarios
-- =========================
-- só usuario e senha são obrigatórios - todo o resto (nome, endereco, cpf, documento, celular,
-- email, observacao, perfil) é opcional, igual pedido no cadastro
CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    usuario VARCHAR(100) NOT NULL,
    senha VARCHAR(255) NOT NULL,
    primeiro_nome VARCHAR(100),
    sobrenome VARCHAR(100),
    cpf VARCHAR(20),
    documento VARCHAR(50),
    celular VARCHAR(20),
    email VARCHAR(255),
    observacao TEXT,
    endereco_id BIGINT,
    perfil_id BIGINT,
    ativo BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_usuarios_usuario UNIQUE (usuario),
    CONSTRAINT fk_usuarios_endereco FOREIGN KEY (endereco_id) REFERENCES enderecos(id),
    CONSTRAINT fk_usuarios_perfil FOREIGN KEY (perfil_id) REFERENCES perfis(id)
);
