-- Financeiro 3B, fase 2: recebimento automatico (origem INFINITEPAY) com a taxa paga pelo cliente gravada a parte.
-- A baixa automatica entra no caixa como qualquer recebimento de banco (banco e forma obrigatorios); a taxa NAO entra no caixa:
-- valor_taxa_cliente e informativa (diferenca entre o pago pelo cliente e o valor do titulo). O usuario (created_by_id) fica nulo nas baixas automaticas.

ALTER TABLE conta_receber_recebimento DROP CONSTRAINT ck_recebimento_origem;
ALTER TABLE conta_receber_recebimento DROP CONSTRAINT ck_recebimento_origem_banco;
ALTER TABLE conta_receber_recebimento ADD CONSTRAINT ck_recebimento_origem CHECK (origem IN ('BANCO', 'CREDITO_CLIENTE', 'INFINITEPAY'));
ALTER TABLE conta_receber_recebimento ADD CONSTRAINT ck_recebimento_origem_banco CHECK (
    (origem IN ('BANCO', 'INFINITEPAY') AND banco_id IS NOT NULL AND forma_pagamento_id IS NOT NULL)
    OR (origem = 'CREDITO_CLIENTE' AND banco_id IS NULL AND forma_pagamento_id IS NULL)
);
ALTER TABLE conta_receber_recebimento ADD COLUMN valor_taxa_cliente NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (valor_taxa_cliente >= 0);
ALTER TABLE conta_receber_recebimento ADD COLUMN link_id BIGINT REFERENCES conta_receber_link_pagamento (id);
ALTER TABLE conta_receber_recebimento ADD COLUMN transaction_nsu VARCHAR(120);
-- um unico recebimento ativo por transacao (idempotencia na ponta do banco de dados)
CREATE UNIQUE INDEX uk_recebimento_transaction_nsu ON conta_receber_recebimento (transaction_nsu) WHERE transaction_nsu IS NOT NULL AND NOT estornada;
CREATE INDEX idx_recebimento_link ON conta_receber_recebimento (link_id);
