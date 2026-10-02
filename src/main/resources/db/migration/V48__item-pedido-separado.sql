-- marca de "já separado" por item do pedido (lista de separação): só pode ser marcada com o pedido
-- em SEPARACAO, e todos os itens ainda disponíveis (não totalmente cancelados) precisam estar
-- separados para o pedido ir para SEPARADO
ALTER TABLE pedidos_produtos ADD COLUMN separado BOOLEAN NOT NULL DEFAULT FALSE;
