-- =========================
-- Agenda: "conferencia" passa a se chamar "pre-acerto".
-- Renomeia a coluna e o indice, preservando os dados. Os CHECKs (datas_por_tipo e ordem_datas) referenciam
-- a coluna pelo numero do atributo, entao o Postgres atualiza a expressao sozinho ao renomear.
-- =========================
ALTER TABLE agenda_marcadores RENAME COLUMN data_conferencia TO data_pre_acerto;

ALTER INDEX idx_agenda_marcadores_data_conferencia RENAME TO idx_agenda_marcadores_data_pre_acerto;
