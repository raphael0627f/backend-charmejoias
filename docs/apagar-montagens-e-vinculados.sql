-- Apaga TODAS as montagens de mostruário e os pedidos e consignações que estão vinculados a elas.
-- Rode no psql:  psql -h localhost -U postgres -d manager_erp -f apagar-montagens-e-vinculados.sql
-- Faça um backup antes (tela de Backups do sistema ou pg_dump). Tudo roda em uma transação:
-- se qualquer comando falhar, nada é apagado.

BEGIN;

-- 1) guarda os ids ANTES de apagar as montagens (apagar a montagem remove os vínculos)
CREATE TEMP TABLE _pedidos_apagar ON COMMIT DROP AS
    SELECT DISTINCT pedido_id AS id FROM montagens_mostruario_pedidos;
CREATE TEMP TABLE _consignacoes_apagar ON COMMIT DROP AS
    SELECT DISTINCT consignacao_id AS id FROM montagens_mostruario_consignacoes;

-- 2) trava: cancela tudo se algo mais depender dessas consignações (pré-acerto, acerto, rota)
DO $$
DECLARE
    n integer;
BEGIN
    SELECT count(*) INTO n FROM pre_acertos_consignacao WHERE consignacao_id IN (SELECT id FROM _consignacoes_apagar);
    IF n > 0 THEN RAISE EXCEPTION 'Existem % pré-acerto(s) ligados a essas consignações; apague-os antes', n; END IF;

    SELECT count(*) INTO n FROM acertos_consignacao WHERE consignacao_id IN (SELECT id FROM _consignacoes_apagar);
    IF n > 0 THEN RAISE EXCEPTION 'Existem % acerto(s) ligados a essas consignações; apague-os antes', n; END IF;

    SELECT count(*) INTO n FROM acertos_joia_consignacoes WHERE consignacao_id IN (SELECT id FROM _consignacoes_apagar);
    IF n > 0 THEN RAISE EXCEPTION 'Existem % acerto(s) de joia ligados a essas consignações; apague-os antes', n; END IF;

    SELECT count(*) INTO n FROM rota_consignacoes_deixar WHERE consignacao_id IN (SELECT id FROM _consignacoes_apagar);
    IF n > 0 THEN RAISE EXCEPTION 'Existem % consignação(ões) em rotas (deixar); retire-as da rota antes', n; END IF;

    SELECT count(*) INTO n FROM rota_consignacoes_trazer WHERE consignacao_id IN (SELECT id FROM _consignacoes_apagar);
    IF n > 0 THEN RAISE EXCEPTION 'Existem % consignação(ões) em rotas (trazer); retire-as da rota antes', n; END IF;
END $$;

-- 3) exclusão
DELETE FROM montagens_mostruario;                                            -- apaga vínculos e instruções em cascata
DELETE FROM pedidos WHERE id IN (SELECT id FROM _pedidos_apagar);            -- apaga os itens dos pedidos em cascata
DELETE FROM consignacoes WHERE id IN (SELECT id FROM _consignacoes_apagar);  -- apaga os produtos das consignações em cascata

-- 4) conferência (deve mostrar 0 nas três primeiras colunas)
SELECT (SELECT count(*) FROM montagens_mostruario)          AS montagens_restantes,
       (SELECT count(*) FROM montagens_mostruario_instrucoes) AS instrucoes_restantes,
       (SELECT count(*) FROM montagens_mostruario_pedidos)  AS vinculos_pedidos_restantes,
       (SELECT count(*) FROM pedidos)                       AS pedidos_total_agora,
       (SELECT count(*) FROM consignacoes)                  AS consignacoes_total_agora;

COMMIT;  -- troque por ROLLBACK para ensaiar sem apagar nada
