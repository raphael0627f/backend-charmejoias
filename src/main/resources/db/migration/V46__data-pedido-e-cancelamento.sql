-- "data do pedido": quando o pedido foi feito, informada pelo usuário (diferente de created_at, que é
-- o timestamp de auditoria de quando o registro foi criado no sistema)
ALTER TABLE pedidos ADD COLUMN data_pedido DATE;
UPDATE pedidos SET data_pedido = created_at::date WHERE data_pedido IS NULL;
ALTER TABLE pedidos ALTER COLUMN data_pedido SET NOT NULL;

-- motivo do cancelamento do pedido inteiro, igual ao padrão de nota fiscal
ALTER TABLE pedidos ADD COLUMN motivo_cancelamento TEXT;

-- cancelamento parcial de item: a quantidade cancelada fica registrada (o item não é removido),
-- junto do motivo
ALTER TABLE pedidos_produtos ADD COLUMN quantidade_cancelada INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pedidos_produtos ADD COLUMN motivo_cancelamento TEXT;
