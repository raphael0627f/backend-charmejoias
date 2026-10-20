-- =========================
-- instrucoes da montagem passam de um texto livre para uma lista de itens que o usuario marca como feitos
-- (o texto livre anterior e descartado: as montagens ja cadastradas eram apenas de teste)
-- =========================
CREATE TABLE montagens_mostruario_instrucoes (
    id BIGSERIAL PRIMARY KEY,
    montagem_id BIGINT NOT NULL REFERENCES montagens_mostruario (id) ON DELETE CASCADE,
    ordem INTEGER NOT NULL,
    texto TEXT NOT NULL,
    concluida BOOLEAN NOT NULL DEFAULT FALSE,
    concluida_em TIMESTAMP,
    concluida_por_id UUID REFERENCES usuarios (id)
);

CREATE INDEX idx_montagens_mostruario_instrucoes_montagem ON montagens_mostruario_instrucoes (montagem_id);

ALTER TABLE montagens_mostruario DROP COLUMN instrucoes;
