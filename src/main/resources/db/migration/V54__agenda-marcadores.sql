-- =========================
-- Agenda: marcadores de dias (TRABALHA com data de conferencia e de acerto, ou NAO_TRABALHA).
-- 1 marcador por dia (UNIQUE em data). Sabado/domingo nao sao gravados. Tabela nasce vazia.
-- =========================
CREATE TABLE agenda_marcadores (
    id BIGSERIAL PRIMARY KEY,
    data DATE NOT NULL,
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    data_conferencia DATE,
    data_acerto DATE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by_id UUID REFERENCES usuarios (id),
    updated_by_id UUID REFERENCES usuarios (id),
    CONSTRAINT uq_agenda_marcadores_data UNIQUE (data),
    CONSTRAINT ck_agenda_marcadores_tipo CHECK (tipo IN ('TRABALHA', 'NAO_TRABALHA')),
    CONSTRAINT ck_agenda_marcadores_datas_por_tipo CHECK (
        (tipo = 'TRABALHA' AND data_conferencia IS NOT NULL AND data_acerto IS NOT NULL)
        OR (tipo = 'NAO_TRABALHA' AND data_conferencia IS NULL AND data_acerto IS NULL)
    ),
    CONSTRAINT ck_agenda_marcadores_ordem_datas CHECK (
        (data_conferencia IS NULL OR data_conferencia >= data)
        AND (data_acerto IS NULL OR data_conferencia IS NULL OR data_acerto >= data_conferencia)
    )
);

CREATE INDEX idx_agenda_marcadores_data_conferencia ON agenda_marcadores (data_conferencia);
CREATE INDEX idx_agenda_marcadores_data_acerto ON agenda_marcadores (data_acerto);

-- permissoes da agenda: ADMIN ja nasce com as duas
INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT perfis.id, todas_permissoes.permissao
FROM perfis
CROSS JOIN (VALUES ('AGENDA_GERENCIAR'), ('AGENDA_VISUALIZAR')) AS todas_permissoes(permissao)
WHERE perfis.nome = 'ADMIN';
