-- =========================
-- TABELA: categorias
-- =========================
CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    ativo BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =========================
-- TABELA: enderecos
-- =========================
CREATE TABLE enderecos (
    id BIGSERIAL PRIMARY KEY,
    logradouro VARCHAR(255),
    numero VARCHAR(50),
    complemento VARCHAR(255),
    bairro VARCHAR(255),
    municipio VARCHAR(255),
    uf VARCHAR(10),
    cep VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =========================
-- TABELA: grupos
-- =========================
CREATE TABLE grupos (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    ativo BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =========================
-- TABELA: fornecedores
-- =========================
CREATE TABLE fornecedores (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255),
    ativo BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- =========================
-- TABELA: clientes
-- =========================
CREATE TABLE clientes (
    id UUID PRIMARY KEY,
    codigo VARCHAR(100),
    referencia VARCHAR(100),
    nome VARCHAR(255),
    filiacao VARCHAR(255),
    cpf VARCHAR(20),
    documento VARCHAR(50),
    genero VARCHAR(20),
    telefone VARCHAR(20),
    celular VARCHAR(20),
    email VARCHAR(255),
    observacao TEXT,
    data_nascimento DATE,
    ativo BOOLEAN,

    endereco_id BIGINT,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_clientes_endereco
        FOREIGN KEY (endereco_id)
        REFERENCES enderecos(id)
);

-- =========================
-- TABELA: produtos
-- =========================
CREATE TABLE produtos (
    id UUID PRIMARY KEY,
    codigo VARCHAR(100),
    referencia VARCHAR(100),
    nome VARCHAR(255),

    preco_custo NUMERIC(10,2),
    preco_venda NUMERIC(10,2),

    ativo BOOLEAN,
    observacoes TEXT,

    categoria_id BIGINT,
    fornecedor_id BIGINT,

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_produtos_categoria
        FOREIGN KEY (categoria_id) REFERENCES categorias(id),

    CONSTRAINT fk_produtos_fornecedor
        FOREIGN KEY (fornecedor_id) REFERENCES fornecedores(id)
);

-- =========================
-- TABELA: clientes_grupos
-- =========================
CREATE TABLE clientes_grupos (
    cliente_id UUID,
    grupo_id BIGINT,

    PRIMARY KEY (cliente_id, grupo_id),

    CONSTRAINT fk_clientes_grupos_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes(id),

    CONSTRAINT fk_clientes_grupos_grupo
        FOREIGN KEY (grupo_id) REFERENCES grupos(id)
);

-- =========================
-- TABELA: produtos_grupos
-- =========================
CREATE TABLE produtos_grupos (
    produto_id UUID,
    grupo_id BIGINT,

    PRIMARY KEY (produto_id, grupo_id),

    CONSTRAINT fk_produtos_grupos_produto
        FOREIGN KEY (produto_id) REFERENCES produtos(id),

    CONSTRAINT fk_produtos_grupos_grupo
        FOREIGN KEY (grupo_id) REFERENCES grupos(id)
);