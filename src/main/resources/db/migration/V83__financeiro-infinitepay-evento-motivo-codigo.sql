-- código estável do motivo da revisão (o texto em "motivo" continua sendo a explicação para o usuário); colunas novas, nulas, sem efeito sobre dados existentes
ALTER TABLE infinitepay_evento ADD COLUMN motivo_codigo VARCHAR(40);
CREATE INDEX idx_infinitepay_evento_motivo_codigo ON infinitepay_evento (motivo_codigo);
