-- Planejador de rotas: pedidos SEPARADO do cliente que o usuario escolhe deixar na visita (quando nao ha consignacao
-- para deixar). Aditiva: tabela nova, nada existente e alterado. Mesmo modelo de planejador_rota_consignacoes_deixar,
-- com a confirmacao de que foi realmente deixado; um pedido so entra uma vez por cliente da rota.
CREATE TABLE planejador_rota_pedidos_deixar (
    id BIGSERIAL PRIMARY KEY,
    planejador_rota_cliente_id BIGINT NOT NULL REFERENCES planejador_rota_clientes (id),
    pedido_id BIGINT NOT NULL REFERENCES pedidos (id),
    confirmada BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_planejador_rota_pedidos_deixar UNIQUE (planejador_rota_cliente_id, pedido_id)
);

CREATE INDEX idx_planejador_rota_pedidos_deixar_pedido ON planejador_rota_pedidos_deixar (pedido_id);
