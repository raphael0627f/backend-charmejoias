-- Financeiro 6.1, fase 2 (Contas a Receber): núcleo do título a receber e o seu histórico.
-- Aditiva: apenas tabelas novas. Recebimento e anexo ficam para as fases 3 e 4.
-- A situação VENCIDO é calculada e nunca gravada.

CREATE SEQUENCE conta_receber_numero_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE conta_receber (
    id BIGSERIAL PRIMARY KEY,
    numero BIGINT NOT NULL,
    cliente_id UUID NOT NULL REFERENCES clientes (id),
    descricao VARCHAR(200) NOT NULL,
    categoria_id BIGINT NOT NULL REFERENCES categoria_receita (id),
    centro_custo_id BIGINT REFERENCES centro_custo (id),
    forma_prevista_id BIGINT REFERENCES forma_pagamento (id),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    valor_recebido NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (valor_recebido >= 0),
    emissao DATE NOT NULL,
    vencimento DATE NOT NULL,
    competencia VARCHAR(7) NOT NULL CHECK (competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    documento VARCHAR(60),
    observacao VARCHAR(1000),
    situacao VARCHAR(20) NOT NULL DEFAULT 'ABERTO' CHECK (situacao IN ('ABERTO', 'RECEBIDO_PARCIAL', 'RECEBIDO', 'CANCELADO')),
    grupo_parcelamento_id UUID,
    parcela_numero INTEGER,
    parcela_total INTEGER,
    cancelamento_motivo VARCHAR(255),
    cancelamento_em TIMESTAMP,
    cancelado_por_id UUID REFERENCES usuarios (id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id),
    CONSTRAINT uk_conta_receber_numero UNIQUE (numero)
);
CREATE INDEX idx_conta_receber_vencimento ON conta_receber (vencimento);
CREATE INDEX idx_conta_receber_situacao ON conta_receber (situacao);
CREATE INDEX idx_conta_receber_cliente ON conta_receber (cliente_id);
CREATE INDEX idx_conta_receber_categoria ON conta_receber (categoria_id);
CREATE INDEX idx_conta_receber_grupo ON conta_receber (grupo_parcelamento_id);

CREATE TABLE conta_receber_evento (
    id BIGSERIAL PRIMARY KEY,
    conta_receber_id BIGINT NOT NULL REFERENCES conta_receber (id),
    evento VARCHAR(40) NOT NULL,
    detalhe VARCHAR(2000),
    usuario_id UUID REFERENCES usuarios (id),
    data_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_conta_receber_evento_conta ON conta_receber_evento (conta_receber_id, id);
