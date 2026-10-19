-- =========================
-- TABELA: montagens_mostruario
-- montagem de um mostruario para um cliente: instrucoes, data de entrega e andamento
-- (ABERTA -> EM_MONTAGEM -> CONCLUIDA, ou CANCELADA com motivo)
-- =========================
CREATE TABLE montagens_mostruario (
    id BIGSERIAL PRIMARY KEY,
    cliente_id UUID NOT NULL REFERENCES clientes (id),
    mostruario_id BIGINT REFERENCES mostruarios (id),
    instrucoes TEXT,
    data_entrega DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    motivo_cancelamento TEXT,
    iniciada_em TIMESTAMP,
    iniciada_por_id UUID REFERENCES usuarios (id),
    concluida_em TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id),
    CONSTRAINT ck_montagens_mostruario_status CHECK (status IN ('ABERTA', 'EM_MONTAGEM', 'CONCLUIDA', 'CANCELADA'))
);

CREATE INDEX idx_montagens_mostruario_cliente ON montagens_mostruario (cliente_id);
CREATE INDEX idx_montagens_mostruario_status ON montagens_mostruario (status);
CREATE INDEX idx_montagens_mostruario_data_entrega ON montagens_mostruario (data_entrega);

-- =========================
-- vinculos da montagem: uma consignacao/pedido so pode estar em uma montagem
-- =========================
CREATE TABLE montagens_mostruario_consignacoes (
    montagem_id BIGINT NOT NULL REFERENCES montagens_mostruario (id) ON DELETE CASCADE,
    consignacao_id BIGINT NOT NULL REFERENCES consignacoes (id),
    PRIMARY KEY (montagem_id, consignacao_id),
    CONSTRAINT uq_montagens_mostruario_consignacao UNIQUE (consignacao_id)
);

CREATE TABLE montagens_mostruario_pedidos (
    montagem_id BIGINT NOT NULL REFERENCES montagens_mostruario (id) ON DELETE CASCADE,
    pedido_id BIGINT NOT NULL REFERENCES pedidos (id),
    PRIMARY KEY (montagem_id, pedido_id),
    CONSTRAINT uq_montagens_mostruario_pedido UNIQUE (pedido_id)
);

-- permissao de gerenciar montagens para o perfil ADMIN
INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT id, 'MONTAGEM_GERENCIAR'
FROM perfis
WHERE perfis.nome = 'ADMIN';
