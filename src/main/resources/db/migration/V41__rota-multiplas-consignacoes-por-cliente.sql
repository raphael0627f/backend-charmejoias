-- agora o cliente pode ter mais de uma consignacao a deixar/trazer por rota, e a confirmacao de
-- entrega/coleta passa a ser por consignacao (nao mais agregada em rota_clientes)
ALTER TABLE rota_clientes DROP COLUMN consignacao_deixar_id;
ALTER TABLE rota_clientes DROP COLUMN consignacao_trazer_id;
ALTER TABLE rota_clientes DROP COLUMN deixou_real;
ALTER TABLE rota_clientes DROP COLUMN trouxe_real;

CREATE TABLE rota_consignacoes_deixar (
    id BIGSERIAL PRIMARY KEY,
    rota_cliente_id BIGINT NOT NULL REFERENCES rota_clientes (id),
    consignacao_id BIGINT NOT NULL REFERENCES consignacoes (id),
    confirmada BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE rota_consignacoes_trazer (
    id BIGSERIAL PRIMARY KEY,
    rota_cliente_id BIGINT NOT NULL REFERENCES rota_clientes (id),
    consignacao_id BIGINT NOT NULL REFERENCES consignacoes (id),
    confirmada BOOLEAN NOT NULL DEFAULT FALSE
);
