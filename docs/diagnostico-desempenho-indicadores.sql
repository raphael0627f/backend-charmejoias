-- DIAGNÓSTICO SOMENTE LEITURA (pode rodar em produção): contagens e plano das consultas dos Indicadores de Consignação (6.7).
-- Não altera dados. Não mostra nomes, documentos nem valores de pessoas. O EXPLAIN ANALYZE só executa SELECTs.

-- 1) tamanho das tabelas envolvidas
SELECT 'acertos_consignacao' AS tabela, count(*) AS linhas FROM acertos_consignacao
UNION ALL SELECT 'acertos_consignacao_produtos', count(*) FROM acertos_consignacao_produtos
UNION ALL SELECT 'consignacoes', count(*) FROM consignacoes
UNION ALL SELECT 'consignacoes_produtos', count(*) FROM consignacoes_produtos
UNION ALL SELECT 'conta_receber', count(*) FROM conta_receber
UNION ALL SELECT 'conta_receber_recebimento', count(*) FROM conta_receber_recebimento;

-- 2) os índices novos da V85 existem? (esperado: 2 linhas)
SELECT indexname FROM pg_indexes
WHERE indexname IN ('idx_acertos_consignacao_produtos_acerto', 'idx_consignacoes_produtos_consignacao');

-- 3) a migration V85 foi aplicada? (esperado: success = true)
SELECT version, success FROM flyway_schema_history WHERE version = '85';

-- 4) tempo da consulta que travava (antes da V85: ~30 s; depois: menos de 0,5 s). Procure "Execution Time" no final.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT a.id, COALESCE(SUM(cp.preco * (cp.quantidade - COALESCE(d.qtd, 0))), 0) AS vendido
FROM acertos_consignacao a
JOIN consignacoes c ON c.id = a.consignacao_id
JOIN consignacoes_produtos cp ON cp.consignacao_id = c.id
LEFT JOIN LATERAL (SELECT SUM(ap.quantidade) AS qtd FROM acertos_consignacao_produtos ap
                   WHERE ap.acerto_consignacao_id = a.id
                     AND ((ap.produto_id IS NOT NULL AND ap.produto_id = cp.produto_id AND ap.produto_variacao_id IS NOT DISTINCT FROM cp.produto_variacao_id)
                          OR (ap.joia_id IS NOT NULL AND ap.joia_id = cp.joia_id))) d ON true
WHERE a.confirmado = true AND c.cancelada = false
GROUP BY a.id;
-- Se aparecer "Seq Scan on acertos_consignacao_produtos ap ... loops=<milhares>", falta o índice (item 2).

-- 5) consultas ativas há mais de 5 s agora (quem está segurando o banco)
SELECT pid, now() - query_start AS duracao, state, left(query, 80) AS inicio_da_consulta
FROM pg_stat_activity
WHERE datname = current_database() AND state <> 'idle' AND now() - query_start > interval '5 seconds'
ORDER BY duracao DESC;
