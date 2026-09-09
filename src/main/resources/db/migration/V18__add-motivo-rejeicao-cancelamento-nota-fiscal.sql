ALTER TABLE notas_fiscais
    ADD COLUMN IF NOT EXISTS motivo_rejeicao_cancelamento TEXT;

-- status ganhou o valor CANCELAMENTO_SOLICITADO (24 caracteres), que não cabia em VARCHAR(20)
ALTER TABLE notas_fiscais
    ALTER COLUMN status TYPE VARCHAR(30);
