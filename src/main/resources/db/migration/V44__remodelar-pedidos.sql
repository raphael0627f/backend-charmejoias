-- pedidos existentes são preservados: viram categoria ENCOMENDA (que não passa por Produção) e os
-- status que deixaram de existir são convertidos (ENTREGUE/FINALIZADO -> ENVIADO, DEVOLVIDO -> CANCELADO)
ALTER TABLE pedidos ADD COLUMN categoria VARCHAR(30);
UPDATE pedidos SET categoria = 'ENCOMENDA';
ALTER TABLE pedidos ALTER COLUMN categoria SET NOT NULL;

UPDATE pedidos SET status = 'ENVIADO' WHERE status IN ('ENTREGUE', 'FINALIZADO');
UPDATE pedidos SET status = 'CANCELADO' WHERE status = 'DEVOLVIDO';

ALTER TABLE pedidos ADD COLUMN impresso BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pedidos ADD COLUMN impresso_em TIMESTAMP;

ALTER TABLE pedidos ADD CONSTRAINT chk_pedidos_categoria
    CHECK (categoria IN ('PERSONALIZADO', 'MONTAGEM', 'ORDEM_SERVICO', 'ENCOMENDA'));

ALTER TABLE pedidos ADD CONSTRAINT chk_pedidos_status
    CHECK (status IN ('PRODUCAO', 'PENDENTE', 'SEPARACAO', 'SEPARADO', 'ENVIADO', 'CANCELADO'));

-- no máximo 1 pedido de Montagem em Produção por cliente (vale também contra requisições concorrentes)
CREATE UNIQUE INDEX uq_pedidos_montagem_producao
    ON pedidos (cliente_id)
    WHERE categoria = 'MONTAGEM' AND status = 'PRODUCAO';

CREATE INDEX idx_pedidos_cliente_id ON pedidos (cliente_id);
CREATE INDEX idx_pedidos_status ON pedidos (status);
CREATE INDEX idx_pedidos_categoria ON pedidos (categoria);
CREATE INDEX idx_pedidos_data_entrega ON pedidos (data_entrega);
