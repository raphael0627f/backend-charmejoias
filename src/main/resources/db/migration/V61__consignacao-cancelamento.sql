-- Cancelamento de consignação: definitivo, só para consignação não confirmada.
-- Aditiva: colunas novas com default (linhas existentes ficam cancelada = false).
ALTER TABLE consignacoes
    ADD COLUMN cancelada BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN motivo_cancelamento TEXT,
    ADD COLUMN cancelada_em TIMESTAMP,
    ADD COLUMN cancelada_por_id UUID REFERENCES usuarios (id);
