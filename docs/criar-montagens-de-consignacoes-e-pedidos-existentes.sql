-- Cria montagens de mostruário a partir de consignações e pedidos JÁ EXISTENTES.
--
-- REGRA
--  1) Base: cada consignação CONFIRMADA com data de acerto no período gera uma montagem do cliente dela, com
--     data de entrega da montagem = essa data de acerto. (Várias confirmadas do mesmo cliente e da mesma data
--     de acerto ficam na mesma montagem; datas de acerto diferentes geram montagens diferentes.)
--  2) A montagem recebe também:
--       - as consignações NÃO confirmadas do cliente cuja data de entrega é igual à data de entrega da montagem;
--       - os pedidos NÃO cancelados do cliente cuja data de entrega é igual à data de entrega da montagem.
--     (As consignações não confirmadas só são consideradas se a data de entrega delas também estiver no período.)
--  3) FICA DE FORA (listado no COMANDO 1): o cliente que tem MAIS DE UMA consignação não confirmada, com data
--     de entrega no período, com datas de entrega DIFERENTES. Para ele nenhuma montagem é criada.
--  Também ficam de fora: consignação sem cliente, os clientes da lista de ignorados e o que já está vinculado
--  a alguma montagem (um item só pode estar em uma) - por isso rodar duas vezes não duplica nada.
--
-- COMO USAR (funciona em qualquer cliente: psql, pgAdmin, DBeaver) - são 3 comandos INDEPENDENTES.
-- Rode UM DE CADA VEZ (selecione o comando inteiro e execute). Não há BEGIN/COMMIT/ROLLBACK:
--   COMANDO 1: relatório do que ficará de FORA          (somente leitura)
--   COMANDO 2: prévia das montagens que SERÃO criadas   (somente leitura)
--   COMANDO 3: criação. Começa em modo ENSAIO (v_ensaio = true): faz tudo, mostra a contagem numa mensagem
--              de erro "ENSAIO ..." e DESFAZ. Para gravar de verdade, troque v_ensaio para false.
-- Faça um backup do banco antes do COMANDO 3 com v_ensaio = false.
-- Se mudar o período ou os códigos ignorados, altere nos 3 comandos.


-- =====================================================================================================
-- COMANDO 1: o que fica de fora (clientes com mais de uma consignação NÃO confirmada de entregas diferentes)
-- =====================================================================================================
WITH param AS (
    SELECT DATE '2026-10-01' AS inicio, DATE '2026-12-31' AS fim,
           ARRAY['10815RF', '2697RF'] AS ignorar
), conf AS (
    SELECT c.id, c.cliente_id, c.data_acerto_consignacao AS data
    FROM consignacoes c
    JOIN clientes cl ON cl.id = c.cliente_id
    JOIN param p ON TRUE
    WHERE c.confirmada = TRUE
      AND c.data_acerto_consignacao BETWEEN p.inicio AND p.fim
      AND cl.codigo <> ALL (p.ignorar)
      AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id)
), naoconf AS (
    SELECT c.id, c.cliente_id, c.data_entrega, c.data_acerto_consignacao AS data_acerto, m.descricao AS kit
    FROM consignacoes c
    LEFT JOIN mostruarios m ON m.id = c.mostruario_id
    JOIN param p ON TRUE
    WHERE c.confirmada = FALSE
      AND c.data_entrega BETWEEN p.inicio AND p.fim                  -- data de entrega também dentro do período
      AND c.cliente_id IN (SELECT cliente_id FROM conf)
      AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id)
), de_fora AS (
    SELECT cliente_id FROM naoconf GROUP BY cliente_id HAVING count(DISTINCT data_entrega) > 1
)
SELECT cl.codigo AS cod_cliente,
       left(cl.nome, 32) AS cliente,
       x.situacao,
       x.consignacao,
       to_char(x.data_entrega, 'DD/MM/YYYY') AS data_entrega,
       to_char(x.data_acerto, 'DD/MM/YYYY')  AS data_acerto,
       x.kit
FROM (
    SELECT n.cliente_id, 'NÃO confirmada (datas de entrega diferentes)' AS situacao, n.id AS consignacao,
           n.data_entrega, n.data_acerto, n.kit, 1 AS ordem
    FROM naoconf n
    WHERE n.cliente_id IN (SELECT cliente_id FROM de_fora)
    UNION ALL
    SELECT c.cliente_id, 'confirmada (geraria a montagem)', c.id, cc.data_entrega, c.data, m.descricao, 2
    FROM conf c
    JOIN consignacoes cc ON cc.id = c.id
    LEFT JOIN mostruarios m ON m.id = cc.mostruario_id
    WHERE c.cliente_id IN (SELECT cliente_id FROM de_fora)
) x
JOIN clientes cl ON cl.id = x.cliente_id
ORDER BY cl.nome, x.ordem, x.data_entrega, x.consignacao;


-- =====================================================================================================
-- COMANDO 2: prévia das montagens que SERÃO criadas
-- =====================================================================================================
WITH param AS (
    SELECT DATE '2026-10-01' AS inicio, DATE '2026-12-31' AS fim,
           ARRAY['10815RF', '2697RF'] AS ignorar
), conf AS (
    SELECT c.id, c.cliente_id, c.data_acerto_consignacao AS data
    FROM consignacoes c
    JOIN clientes cl ON cl.id = c.cliente_id
    JOIN param p ON TRUE
    WHERE c.confirmada = TRUE
      AND c.data_acerto_consignacao BETWEEN p.inicio AND p.fim
      AND cl.codigo <> ALL (p.ignorar)
      AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id)
), naoconf AS (
    SELECT c.id, c.cliente_id, c.data_entrega
    FROM consignacoes c
    JOIN param p ON TRUE
    WHERE c.confirmada = FALSE
      AND c.data_entrega BETWEEN p.inicio AND p.fim                  -- data de entrega também dentro do período
      AND c.cliente_id IN (SELECT cliente_id FROM conf)
      AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id)
), de_fora AS (
    SELECT cliente_id FROM naoconf GROUP BY cliente_id HAVING count(DISTINCT data_entrega) > 1
), grupos AS (
    SELECT DISTINCT cliente_id, data FROM conf WHERE cliente_id NOT IN (SELECT cliente_id FROM de_fora)
)
SELECT cl.codigo AS cod_cliente,
       left(cl.nome, 32) AS cliente,
       to_char(g.data, 'DD/MM/YYYY') AS data_entrega_montagem,
       (SELECT count(*) FROM conf c    WHERE c.cliente_id = g.cliente_id AND c.data = g.data)         AS confirmadas,
       (SELECT count(*) FROM naoconf n WHERE n.cliente_id = g.cliente_id AND n.data_entrega = g.data) AS nao_confirmadas,
       (SELECT count(*) FROM pedidos p WHERE p.cliente_id = g.cliente_id AND p.data_entrega = g.data
                                         AND p.status <> 'CANCELADO'
                                         AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_pedidos mp WHERE mp.pedido_id = p.id)) AS pedidos
FROM grupos g
JOIN clientes cl ON cl.id = g.cliente_id
ORDER BY g.data, cl.nome;


-- =====================================================================================================
-- COMANDO 3: criação das montagens (atômico: um único comando; se der erro, nada é gravado)
--   v_ensaio = true  -> faz tudo, mostra a contagem em uma mensagem "ENSAIO ..." (aparece como erro) e DESFAZ
--   v_ensaio = false -> GRAVA de verdade e mostra a contagem numa mensagem (NOTICE)
-- =====================================================================================================
DO $$
DECLARE
    v_ensaio  boolean := true;                                   -- <<< troque para false para GRAVAR
    v_inicio  date    := DATE '2026-10-01';
    v_fim     date    := DATE '2026-12-31';
    v_ignorar text[]  := ARRAY['10815RF', '2697RF'];

    g        record;
    nova     bigint;
    n        integer;
    q_mont   integer := 0;
    q_cons   integer := 0;
    q_ped    integer := 0;
    q_fora   integer := 0;
    resumo   text;
BEGIN
    -- clientes deixados de fora (mais de uma não confirmada com datas de entrega diferentes)
    SELECT count(*) INTO q_fora FROM (
        SELECT c.cliente_id
        FROM consignacoes c
        WHERE c.confirmada = FALSE
          AND c.data_entrega BETWEEN v_inicio AND v_fim              -- data de entrega também dentro do período
          AND c.cliente_id IN (
              SELECT c2.cliente_id FROM consignacoes c2 JOIN clientes cl ON cl.id = c2.cliente_id
              WHERE c2.confirmada = TRUE AND c2.data_acerto_consignacao BETWEEN v_inicio AND v_fim
                AND cl.codigo <> ALL (v_ignorar)
                AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c2.id))
          AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id)
        GROUP BY c.cliente_id HAVING count(DISTINCT c.data_entrega) > 1
    ) t;

    FOR g IN
        WITH conf AS (
            SELECT c.cliente_id, c.data_acerto_consignacao AS data
            FROM consignacoes c
            JOIN clientes cl ON cl.id = c.cliente_id
            WHERE c.confirmada = TRUE
              AND c.data_acerto_consignacao BETWEEN v_inicio AND v_fim
              AND cl.codigo <> ALL (v_ignorar)
              AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id)
        ), de_fora AS (
            SELECT c.cliente_id
            FROM consignacoes c
            WHERE c.confirmada = FALSE
              AND c.data_entrega BETWEEN v_inicio AND v_fim          -- data de entrega também dentro do período
              AND c.cliente_id IN (SELECT cliente_id FROM conf)
              AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id)
            GROUP BY c.cliente_id HAVING count(DISTINCT c.data_entrega) > 1
        )
        SELECT DISTINCT cliente_id, data FROM conf
        WHERE cliente_id NOT IN (SELECT cliente_id FROM de_fora)
        ORDER BY data, cliente_id
    LOOP
        INSERT INTO montagens_mostruario (cliente_id, data_entrega, status, created_at, updated_at)
        VALUES (g.cliente_id, g.data, 'ABERTA', now(), now())
        RETURNING id INTO nova;
        q_mont := q_mont + 1;

        -- consignação(ões) confirmada(s) que originaram a montagem
        INSERT INTO montagens_mostruario_consignacoes (montagem_id, consignacao_id)
        SELECT nova, c.id FROM consignacoes c
        WHERE c.confirmada = TRUE AND c.cliente_id = g.cliente_id AND c.data_acerto_consignacao = g.data
          AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id);
        GET DIAGNOSTICS n = ROW_COUNT;  q_cons := q_cons + n;

        -- consignações não confirmadas com a mesma data de entrega da montagem
        INSERT INTO montagens_mostruario_consignacoes (montagem_id, consignacao_id)
        SELECT nova, c.id FROM consignacoes c
        WHERE c.confirmada = FALSE AND c.cliente_id = g.cliente_id AND c.data_entrega = g.data
          AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_consignacoes mc WHERE mc.consignacao_id = c.id);
        GET DIAGNOSTICS n = ROW_COUNT;  q_cons := q_cons + n;

        -- pedidos não cancelados com a mesma data de entrega da montagem
        INSERT INTO montagens_mostruario_pedidos (montagem_id, pedido_id)
        SELECT nova, p.id FROM pedidos p
        WHERE p.cliente_id = g.cliente_id AND p.data_entrega = g.data AND p.status <> 'CANCELADO'
          AND NOT EXISTS (SELECT 1 FROM montagens_mostruario_pedidos mp WHERE mp.pedido_id = p.id);
        GET DIAGNOSTICS n = ROW_COUNT;  q_ped := q_ped + n;
    END LOOP;

    resumo := format('%s montagem(ns), %s consignação(ões) e %s pedido(s) vinculados; %s cliente(s) deixado(s) de fora',
                     q_mont, q_cons, q_ped, q_fora);

    IF v_ensaio THEN
        RAISE EXCEPTION 'ENSAIO (nada foi gravado): %', resumo;
    END IF;
    RAISE NOTICE 'CRIADO: %', resumo;
END $$;
