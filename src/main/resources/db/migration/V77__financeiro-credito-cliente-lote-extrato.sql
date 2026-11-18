-- Financeiro 6.3 (crédito do cliente), fase 2: lotes de crédito e extrato (append-only).
-- Somente tabelas novas: nenhuma constraint ou coluna existente é alterada. Crédito sem validade.

CREATE TABLE credito_cliente_lote (
    id BIGSERIAL PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES clientes (id),
    origem_tipo VARCHAR(30) NOT NULL CHECK (origem_tipo IN ('EXCEDENTE_ACERTO', 'LANCAMENTO_MANUAL')),
    origem_id BIGINT,
    valor_original NUMERIC(15, 2) NOT NULL CHECK (valor_original > 0),
    valor_utilizado NUMERIC(15, 2) NOT NULL DEFAULT 0,
    situacao VARCHAR(20) NOT NULL DEFAULT 'ATIVO' CHECK (situacao IN ('ATIVO', 'DESFEITO')),
    motivo VARCHAR(500),
    created_by_id UUID REFERENCES usuarios (id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_credito_lote_utilizado CHECK (valor_utilizado >= 0 AND valor_utilizado <= valor_original),
    CONSTRAINT ck_credito_lote_origem CHECK ((origem_tipo = 'EXCEDENTE_ACERTO' AND origem_id IS NOT NULL) OR (origem_tipo = 'LANCAMENTO_MANUAL' AND motivo IS NOT NULL))
);
CREATE INDEX idx_credito_lote_cliente ON credito_cliente_lote (cliente_id, situacao);
-- um único lote ativo por excedente
CREATE UNIQUE INDEX uk_credito_lote_excedente_ativo ON credito_cliente_lote (origem_id) WHERE origem_tipo = 'EXCEDENTE_ACERTO' AND situacao = 'ATIVO';

CREATE TABLE credito_cliente_movimento (
    id BIGSERIAL PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES clientes (id),
    lote_id BIGINT NOT NULL REFERENCES credito_cliente_lote (id),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('ENTRADA', 'SAIDA', 'ESTORNO_SAIDA', 'ESTORNO_ENTRADA')),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    conta_receber_id BIGINT REFERENCES conta_receber (id),
    recebimento_id BIGINT REFERENCES conta_receber_recebimento (id),
    motivo VARCHAR(500),
    usuario_id UUID REFERENCES usuarios (id),
    data_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_credito_movimento_cliente ON credito_cliente_movimento (cliente_id, id);
CREATE INDEX idx_credito_movimento_lote ON credito_cliente_movimento (lote_id);
