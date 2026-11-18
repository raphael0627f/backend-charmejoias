-- Financeiro 6.3 (crédito do cliente), fase 4: cliente como favorecido no Contas a Pagar e título de devolução do excedente.
-- Colunas novas e anuláveis em conta_pagar (títulos existentes seguem como avulsos, sem vínculo automático) e a categoria de despesa
-- "Devoluções a clientes" (grupo de nível 1, usada automaticamente nos títulos de devolução).

ALTER TABLE conta_pagar ADD COLUMN cliente_id UUID REFERENCES clientes (id);
ALTER TABLE conta_pagar ADD COLUMN excedente_id BIGINT REFERENCES excedente_acerto (id);
ALTER TABLE conta_pagar ADD CONSTRAINT ck_conta_pagar_excedente_cliente CHECK (excedente_id IS NULL OR cliente_id IS NOT NULL);
CREATE INDEX idx_conta_pagar_cliente ON conta_pagar (cliente_id);
-- um único título a pagar ativo (não cancelado) por excedente
CREATE UNIQUE INDEX uk_conta_pagar_excedente_ativo ON conta_pagar (excedente_id) WHERE excedente_id IS NOT NULL AND situacao <> 'CANCELADO';

INSERT INTO categoria_despesa (nome, pai_id, tipo, ordem, created_at, updated_at)
SELECT 'Devoluções a clientes', NULL, 'OUTROS', 8, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM categoria_despesa WHERE pai_id IS NULL AND LOWER(nome) = LOWER('Devoluções a clientes'));
