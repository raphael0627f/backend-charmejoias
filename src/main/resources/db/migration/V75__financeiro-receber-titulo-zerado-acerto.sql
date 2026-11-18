-- Financeiro 6.3: título a receber de R$ 0,00, já quitado, gerado automaticamente ao confirmar um acerto sem valor a pagar (apenas registro).
-- Relaxa somente a constraint de valor: o zero é aceito exclusivamente em título de ACERTO já RECEBIDO. Aditiva: não altera dados existentes.

ALTER TABLE conta_receber DROP CONSTRAINT conta_receber_valor_check;
ALTER TABLE conta_receber ADD CONSTRAINT conta_receber_valor_check CHECK (
    valor > 0 OR (valor = 0 AND acerto_id IS NOT NULL AND situacao = 'RECEBIDO')
);
