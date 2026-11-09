-- =========================
-- Agenda: cadastro de rotas (nome + cidades do catalogo) e marcadores TRABALHA ligados a uma rota,
-- com cidades extras so daquele dia.
-- IRREVERSIVEL: apaga TODOS os marcadores existentes (teste e producao) antes de criar as regras novas.
-- =========================
DELETE FROM agenda_marcadores;

CREATE TABLE agenda_rotas (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id)
);

-- nome unico sem diferenciar maiuscula/minuscula
CREATE UNIQUE INDEX uq_agenda_rotas_nome ON agenda_rotas (lower(nome));

-- cidades da rota; "ordem" preserva a ordem escolhida pelo usuario
CREATE TABLE agenda_rota_cidades (
    rota_id BIGINT NOT NULL REFERENCES agenda_rotas (id) ON DELETE CASCADE,
    cidade_id BIGINT NOT NULL REFERENCES cidades (id),
    ordem INTEGER NOT NULL,
    PRIMARY KEY (rota_id, ordem),
    CONSTRAINT uq_agenda_rota_cidades UNIQUE (rota_id, cidade_id)
);

CREATE INDEX idx_agenda_rota_cidades_cidade ON agenda_rota_cidades (cidade_id);

-- TRABALHA passa a apontar para uma rota (o nome vem da rota); NAO_TRABALHA continua com nome proprio
ALTER TABLE agenda_marcadores ALTER COLUMN nome DROP NOT NULL;
ALTER TABLE agenda_marcadores ADD COLUMN rota_id BIGINT REFERENCES agenda_rotas (id);
ALTER TABLE agenda_marcadores ADD CONSTRAINT ck_agenda_marcadores_rota_por_tipo CHECK (
    (tipo = 'TRABALHA' AND rota_id IS NOT NULL AND nome IS NULL)
    OR (tipo = 'NAO_TRABALHA' AND rota_id IS NULL AND nome IS NOT NULL)
);
CREATE INDEX idx_agenda_marcadores_rota ON agenda_marcadores (rota_id);

-- cidades extras do dia (nao entram na rota)
CREATE TABLE agenda_marcador_cidades_extras (
    marcador_id BIGINT NOT NULL REFERENCES agenda_marcadores (id) ON DELETE CASCADE,
    cidade_id BIGINT NOT NULL REFERENCES cidades (id),
    ordem INTEGER NOT NULL,
    PRIMARY KEY (marcador_id, ordem),
    CONSTRAINT uq_agenda_marcador_cidades_extras UNIQUE (marcador_id, cidade_id)
);

CREATE INDEX idx_agenda_marcador_cidades_extras_cidade ON agenda_marcador_cidades_extras (cidade_id);
