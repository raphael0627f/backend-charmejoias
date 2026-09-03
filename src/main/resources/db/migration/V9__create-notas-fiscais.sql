-- =========================
-- TABELA: notas_fiscais
-- =========================
CREATE TABLE notas_fiscais (
    id BIGSERIAL PRIMARY KEY,

    empresa_id BIGINT,
    cliente_id UUID,

    serie INTEGER,
    numero BIGINT,

    natureza_operacao VARCHAR(60),
    consumidor_final BOOLEAN NOT NULL DEFAULT TRUE,
    indicador_presenca INTEGER,

    ambiente VARCHAR(20),
    status VARCHAR(20),

    chave_acesso VARCHAR(44),
    protocolo_autorizacao VARCHAR(50),
    motivo_rejeicao TEXT,
    contora_document_id VARCHAR(100),

    created_at TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT fk_notas_fiscais_empresa
        FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT fk_notas_fiscais_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

-- =========================
-- TABELA: notas_fiscais_itens
-- =========================
CREATE TABLE notas_fiscais_itens (
    id BIGSERIAL PRIMARY KEY,

    nota_fiscal_id BIGINT,
    produto_id UUID,

    quantidade NUMERIC(12,4),
    valor_unitario NUMERIC(10,2),
    valor_total NUMERIC(10,2),
    desconto NUMERIC(10,2),
    cfop VARCHAR(4),

    CONSTRAINT fk_notas_fiscais_itens_nota
        FOREIGN KEY (nota_fiscal_id) REFERENCES notas_fiscais(id),
    CONSTRAINT fk_notas_fiscais_itens_produto
        FOREIGN KEY (produto_id) REFERENCES produtos(id)
);

-- =========================
-- TABELA: notas_fiscais_pagamentos
-- =========================
CREATE TABLE notas_fiscais_pagamentos (
    id BIGSERIAL PRIMARY KEY,

    nota_fiscal_id BIGINT,
    forma_pagamento VARCHAR(5),
    valor NUMERIC(10,2),

    CONSTRAINT fk_notas_fiscais_pagamentos_nota
        FOREIGN KEY (nota_fiscal_id) REFERENCES notas_fiscais(id)
);
