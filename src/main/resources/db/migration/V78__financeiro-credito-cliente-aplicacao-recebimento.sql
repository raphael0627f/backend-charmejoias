-- Financeiro 6.3 (crédito do cliente), fase 3: aplicação do crédito como recebimento SEM banco (fora do caixa).
-- conta_receber_recebimento ganha a coluna "origem". banco_id e forma_pagamento_id passam a aceitar nulo SOMENTE quando origem = 'CREDITO_CLIENTE';
-- os recebimentos existentes (todos com banco e forma) ficam origem 'BANCO' e continuam obrigados a ter ambos. Crédito não gera movimentacao_banco.

ALTER TABLE conta_receber_recebimento ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'BANCO';
ALTER TABLE conta_receber_recebimento ADD CONSTRAINT ck_recebimento_origem CHECK (origem IN ('BANCO', 'CREDITO_CLIENTE'));
ALTER TABLE conta_receber_recebimento ALTER COLUMN banco_id DROP NOT NULL;
ALTER TABLE conta_receber_recebimento ALTER COLUMN forma_pagamento_id DROP NOT NULL;
ALTER TABLE conta_receber_recebimento ADD CONSTRAINT ck_recebimento_origem_banco CHECK (
    (origem = 'BANCO' AND banco_id IS NOT NULL AND forma_pagamento_id IS NOT NULL)
    OR (origem = 'CREDITO_CLIENTE' AND banco_id IS NULL AND forma_pagamento_id IS NULL)
);
CREATE INDEX idx_conta_receber_recebimento_origem ON conta_receber_recebimento (origem);
CREATE INDEX idx_credito_movimento_recebimento ON credito_cliente_movimento (recebimento_id);
