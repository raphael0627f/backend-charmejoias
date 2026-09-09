-- =========================
-- PRODUTOS: renomear codigo -> sku, adicionar tipo, imagem e estoque
-- =========================
ALTER TABLE produtos RENAME COLUMN codigo TO sku;

ALTER TABLE produtos ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'SIMPLES';
ALTER TABLE produtos ALTER COLUMN tipo DROP DEFAULT;

ALTER TABLE produtos ADD COLUMN imagem_url VARCHAR(500);

ALTER TABLE produtos ADD COLUMN quantidade_estoque INTEGER NOT NULL DEFAULT 0;
ALTER TABLE produtos ALTER COLUMN quantidade_estoque DROP DEFAULT;

-- =========================
-- TABELA: variacoes
-- =========================
CREATE TABLE variacoes (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    ativo BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =========================
-- TABELA: itens_variacao
-- =========================
CREATE TABLE itens_variacao (
    id BIGSERIAL PRIMARY KEY,
    variacao_id BIGINT NOT NULL,
    nome VARCHAR(255),
    valor VARCHAR(50),
    ativo BOOLEAN,

    CONSTRAINT fk_itens_variacao_variacao
        FOREIGN KEY (variacao_id) REFERENCES variacoes(id)
);

-- =========================
-- TABELA: produtos_variacoes
-- =========================
CREATE TABLE produtos_variacoes (
    id BIGSERIAL PRIMARY KEY,
    produto_id UUID NOT NULL,
    sku VARCHAR(150) NOT NULL,
    preco_custo NUMERIC(10,2),
    preco_venda NUMERIC(10,2),
    quantidade_estoque INTEGER NOT NULL DEFAULT 0,
    ativo BOOLEAN,
    codigo_barras VARCHAR(30),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_produtos_variacoes_produto
        FOREIGN KEY (produto_id) REFERENCES produtos(id),
    CONSTRAINT uq_produtos_variacoes_sku UNIQUE (sku),
    CONSTRAINT uq_produtos_variacoes_codigo_barras UNIQUE (codigo_barras)
);

-- =========================
-- TABELA: produtos_variacoes_itens
-- =========================
CREATE TABLE produtos_variacoes_itens (
    id BIGSERIAL PRIMARY KEY,
    produto_variacao_id BIGINT NOT NULL,
    item_variacao_id BIGINT NOT NULL,

    CONSTRAINT fk_produtos_variacoes_itens_produto_variacao
        FOREIGN KEY (produto_variacao_id) REFERENCES produtos_variacoes(id),
    CONSTRAINT fk_produtos_variacoes_itens_item_variacao
        FOREIGN KEY (item_variacao_id) REFERENCES itens_variacao(id),
    CONSTRAINT uq_produtos_variacoes_itens UNIQUE (produto_variacao_id, item_variacao_id)
);
