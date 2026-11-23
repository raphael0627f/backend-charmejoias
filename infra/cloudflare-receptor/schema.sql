-- D1 do receptor de pagamentos (charme-pagamentos-db). Idempotente: pode ser reaplicado.
CREATE TABLE IF NOT EXISTS fila (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recebido_em TEXT NOT NULL,
    evento TEXT NOT NULL,
    order_nsu TEXT,
    transaction_nsu TEXT NOT NULL,
    invoice_slug TEXT,
    corpo TEXT NOT NULL,
    ack_em TEXT,
    UNIQUE (transaction_nsu, evento)
);
CREATE INDEX IF NOT EXISTS idx_fila_pendentes ON fila (ack_em, id);
CREATE INDEX IF NOT EXISTS idx_fila_ack ON fila (ack_em);
