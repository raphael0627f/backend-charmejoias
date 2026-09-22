CREATE TABLE rotas (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    data_rota DATE NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id)
);

-- o cliente pode ter no maximo uma consignacao a deixar e uma a trazer por rota
CREATE TABLE rota_clientes (
    id BIGSERIAL PRIMARY KEY,
    rota_id BIGINT NOT NULL REFERENCES rotas (id),
    cliente_id UUID NOT NULL REFERENCES clientes (id),
    endereco_id BIGINT NOT NULL REFERENCES enderecos (id),
    ordem INTEGER,
    observacao VARCHAR(255),
    consignacao_deixar_id BIGINT REFERENCES consignacoes (id),
    consignacao_trazer_id BIGINT REFERENCES consignacoes (id),
    deixou_real BOOLEAN,
    trouxe_real BOOLEAN,
    UNIQUE (rota_id, cliente_id)
);

INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'ROTA_GERENCIAR'
FROM perfis
WHERE perfis.nome = 'ADMIN';
