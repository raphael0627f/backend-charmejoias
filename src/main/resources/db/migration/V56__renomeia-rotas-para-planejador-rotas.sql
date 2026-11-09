-- =========================
-- O modulo "Rotas" passa a se chamar "Planejador de rotas" (a palavra "rota" fica livre para a rota da Agenda).
-- So renomeia objetos de catalogo (tabelas, colunas, constraints, indices, sequences) e a permissao; nenhum dado e alterado.
--   rotas                        -> planejador_rotas
--   rota_clientes                -> planejador_rota_clientes            (rota_id -> planejador_rota_id)
--   rota_consignacoes_deixar     -> planejador_rota_consignacoes_deixar (rota_cliente_id -> planejador_rota_cliente_id)
--   rota_consignacoes_trazer     -> planejador_rota_consignacoes_trazer (rota_cliente_id -> planejador_rota_cliente_id)
--   permissao ROTA_GERENCIAR     -> PLANEJADOR_ROTA_GERENCIAR (linhas ja gravadas em perfis_permissoes)
-- =========================
ALTER TABLE rotas RENAME TO planejador_rotas;
ALTER TABLE rota_clientes RENAME TO planejador_rota_clientes;
ALTER TABLE rota_consignacoes_deixar RENAME TO planejador_rota_consignacoes_deixar;
ALTER TABLE rota_consignacoes_trazer RENAME TO planejador_rota_consignacoes_trazer;

ALTER TABLE planejador_rota_clientes RENAME COLUMN rota_id TO planejador_rota_id;
ALTER TABLE planejador_rota_consignacoes_deixar RENAME COLUMN rota_cliente_id TO planejador_rota_cliente_id;
ALTER TABLE planejador_rota_consignacoes_trazer RENAME COLUMN rota_cliente_id TO planejador_rota_cliente_id;

-- constraints (PK, FK, UNIQUE; renomear a constraint renomeia o indice que a sustenta), depois indices avulsos e sequences:
-- os nomes sao os gerados pelo Postgres ("rotas_pkey", "rota_clientes_rota_id_fkey", ...), todos com prefixo "rota"
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conname, c.conrelid::regclass::text AS tabela
        FROM pg_constraint c
        WHERE c.conrelid IN ('planejador_rotas'::regclass, 'planejador_rota_clientes'::regclass,
                             'planejador_rota_consignacoes_deixar'::regclass, 'planejador_rota_consignacoes_trazer'::regclass)
          AND c.conname LIKE 'rota%'
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I', r.tabela, r.conname,
                       regexp_replace(r.conname, '^rota', 'planejador_rota'));
    END LOOP;

    FOR r IN
        SELECT i.indexname
        FROM pg_indexes i
        WHERE i.schemaname = current_schema()
          AND i.tablename IN ('planejador_rotas', 'planejador_rota_clientes',
                              'planejador_rota_consignacoes_deixar', 'planejador_rota_consignacoes_trazer')
          AND i.indexname LIKE 'rota%'
    LOOP
        EXECUTE format('ALTER INDEX %I RENAME TO %I', r.indexname, regexp_replace(r.indexname, '^rota', 'planejador_rota'));
    END LOOP;

    FOR r IN
        SELECT s.sequencename
        FROM pg_sequences s
        WHERE s.schemaname = current_schema()
          AND s.sequencename IN ('rotas_id_seq', 'rota_clientes_id_seq', 'rota_consignacoes_deixar_id_seq', 'rota_consignacoes_trazer_id_seq')
    LOOP
        EXECUTE format('ALTER SEQUENCE %I RENAME TO %I', r.sequencename, regexp_replace(r.sequencename, '^rota', 'planejador_rota'));
    END LOOP;
END $$;

UPDATE perfis_permissoes SET permissao = 'PLANEJADOR_ROTA_GERENCIAR' WHERE permissao = 'ROTA_GERENCIAR';
