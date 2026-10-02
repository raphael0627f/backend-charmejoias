-- a categoria MONTAGEM e todas as regras específicas dela (pedido único em produção por
-- cliente, geração automática a partir da consignação) deixaram de existir - remove os
-- registros existentes dessa categoria e as restrições do banco relacionadas a ela
DELETE FROM pedidos_produtos WHERE pedido_id IN (SELECT id FROM pedidos WHERE categoria = 'MONTAGEM');
DELETE FROM pedidos WHERE categoria = 'MONTAGEM';

DROP INDEX IF EXISTS uq_pedidos_montagem_producao;

ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS chk_pedidos_categoria;
ALTER TABLE pedidos ADD CONSTRAINT chk_pedidos_categoria
    CHECK (categoria IN ('PERSONALIZADO', 'ORDEM_SERVICO', 'ENCOMENDA'));
