-- Financeiro 6.3 (crédito do cliente), fase 1: excedente do acerto de consignação, persistido e "A DESTINAR".
-- Somente tabelas novas: nenhuma constraint ou coluna existente é alterada. Sem dados retroativos (vale a partir da confirmação do acerto).
-- Permissões novas (somente o perfil ADMIN; relogar após conceder).

CREATE TABLE excedente_acerto (
    id BIGSERIAL PRIMARY KEY,
    acerto_id BIGINT NOT NULL REFERENCES acertos_consignacao (id),
    consignacao_id BIGINT NOT NULL REFERENCES consignacoes (id),
    cliente_id UUID NOT NULL REFERENCES clientes (id),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    situacao VARCHAR(20) NOT NULL DEFAULT 'A_DESTINAR' CHECK (situacao IN ('A_DESTINAR', 'CREDITO', 'DEVOLUCAO', 'CANCELADO')),
    destino VARCHAR(20) CHECK (destino IS NULL OR destino IN ('CREDITO', 'DEVOLUCAO')),
    destinado_em TIMESTAMP,
    destinado_por_id UUID REFERENCES usuarios (id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_excedente_acerto_acerto UNIQUE (acerto_id)
);
CREATE INDEX idx_excedente_acerto_cliente ON excedente_acerto (cliente_id);
CREATE INDEX idx_excedente_acerto_consignacao ON excedente_acerto (consignacao_id);
CREATE INDEX idx_excedente_acerto_situacao ON excedente_acerto (situacao);

CREATE TABLE excedente_acerto_evento (
    id BIGSERIAL PRIMARY KEY,
    excedente_id BIGINT NOT NULL REFERENCES excedente_acerto (id),
    evento VARCHAR(40) NOT NULL,
    detalhe VARCHAR(2000),
    usuario_id UUID REFERENCES usuarios (id),
    data_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_excedente_acerto_evento ON excedente_acerto_evento (excedente_id, id);

INSERT INTO perfis_permissoes (perfil_id, permissao)
SELECT p.id, perm.nome
FROM perfis p
CROSS JOIN (VALUES
    ('CREDITO_CLIENTE_VISUALIZAR'), ('CREDITO_CLIENTE_GERENCIAR'), ('CREDITO_CLIENTE_APLICAR')
) AS perm (nome)
WHERE p.nome = 'ADMIN';
