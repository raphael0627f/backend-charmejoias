-- Financeiro 6.1: título gerado a partir de pré-acerto SEM consignação de referência.
-- O pré-acerto pode existir sem consignação (consignacao_id anulável desde a V13); o acerto continua exigindo a consignação.
-- Troca apenas o CHECK da V72: não altera dados existentes.

ALTER TABLE conta_receber DROP CONSTRAINT ck_conta_receber_origem;
ALTER TABLE conta_receber ADD CONSTRAINT ck_conta_receber_origem CHECK (
    NOT (pre_acerto_id IS NOT NULL AND acerto_id IS NOT NULL)
    AND (acerto_id IS NULL OR consignacao_id IS NOT NULL)
);
