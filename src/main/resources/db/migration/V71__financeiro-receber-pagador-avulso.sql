-- Financeiro 6.1 (Contas a Receber): pagador avulso. O título passa a poder ser lançado sem cliente cadastrado.
-- Somente DDL aditiva: nenhum dado existente é alterado (todos os títulos atuais têm cliente e continuam válidos na restrição).

ALTER TABLE conta_receber ALTER COLUMN cliente_id DROP NOT NULL;
ALTER TABLE conta_receber ADD COLUMN pagador_nome VARCHAR(150);
ALTER TABLE conta_receber ADD COLUMN pagador_documento VARCHAR(20);

-- ou há cliente (e nenhum dado de pagador avulso), ou há pagador avulso com nome preenchido
ALTER TABLE conta_receber ADD CONSTRAINT ck_conta_receber_pagador CHECK (
    (cliente_id IS NOT NULL AND pagador_nome IS NULL AND pagador_documento IS NULL)
    OR (cliente_id IS NULL AND pagador_nome IS NOT NULL AND length(btrim(pagador_nome)) > 0)
);

-- busca por nome do pagador avulso (os índices por cliente_id e por cliente/situação continuam)
CREATE INDEX idx_conta_receber_pagador_nome ON conta_receber (pagador_nome) WHERE pagador_nome IS NOT NULL;
