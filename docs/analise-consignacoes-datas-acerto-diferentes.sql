-- Consignações do mesmo cliente com datas de acerto DIFERENTES, de 01/10 a 31/12/2026
-- (somente consignações NÃO confirmadas; ignora consignação sem cliente e os clientes 10815RF e 2697RF).
-- Somente leitura.
WITH base AS (
    SELECT c.id, c.cliente_id, cl.codigo, cl.nome, c.data_acerto_consignacao AS data_acerto,
           c.data_entrega, c.confirmada, m.descricao AS kit
    FROM consignacoes c
    JOIN clientes cl ON cl.id = c.cliente_id                 -- ignora consignações sem cliente
    LEFT JOIN mostruarios m ON m.id = c.mostruario_id
    WHERE c.data_acerto_consignacao BETWEEN '2026-10-01' AND '2026-12-31'
      AND c.confirmada = FALSE                                -- só consignações NÃO confirmadas
      AND cl.codigo NOT IN ('10815RF', '2697RF')
), varios AS (
    SELECT cliente_id FROM base GROUP BY cliente_id HAVING count(DISTINCT data_acerto) > 1
)
SELECT b.codigo AS cod_cliente, left(b.nome, 32) AS cliente, b.id AS consignacao, b.kit,
       to_char(b.data_acerto, 'DD/MM/YYYY') AS data_acerto,
       to_char(b.data_entrega, 'DD/MM/YYYY') AS data_entrega,
       CASE WHEN b.confirmada THEN 'sim' ELSE 'nao' END AS confirmada
FROM base b JOIN varios v ON v.cliente_id = b.cliente_id
ORDER BY b.nome, b.data_acerto, b.id;
