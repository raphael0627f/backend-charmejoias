-- =========================
-- TABELA: joias
-- =========================
CREATE TABLE joias (
    id UUID PRIMARY KEY,
    codigo VARCHAR(100),
    referencia VARCHAR(100),
    nome VARCHAR(255),

    preco_venda NUMERIC(10,2),

    ativo BOOLEAN,
    observacoes TEXT,

    categoria_id BIGINT,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_joias_categoria
        FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

-- =========================
-- TABELA: joias_grupos
-- =========================
CREATE TABLE joias_grupos (
    joia_id UUID,
    grupo_id BIGINT,

    PRIMARY KEY (joia_id, grupo_id),

    CONSTRAINT fk_joias_grupos_joia
        FOREIGN KEY (joia_id) REFERENCES joias(id),

    CONSTRAINT fk_joias_grupos_grupo
        FOREIGN KEY (grupo_id) REFERENCES grupos(id)
);
