package com.manager.app.shared;

import com.manager.app.shared.exception.ConsultaDemoradaException;
import com.manager.app.shared.exception.SistemaOcupadoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

// salvaguarda das consultas pesadas (indicadores e fluxo de caixa): no máximo N ao mesmo tempo (as demais esperam um pouco e depois recebem 503),
// cada consulta com limite de tempo no banco (statement_timeout; estourou = 504). Assim uma consulta ruim nunca segura todas as conexões do pool nem trava o sistema.
// A espera pelo semáforo acontece ANTES de abrir a transação, para não prender conexão do pool enquanto aguarda.
@Slf4j
@Component
public class ConsultaPesada {

    public static final String MENSAGEM_TEMPO = "A consulta excedeu o tempo limite. Reduza o período.";
    public static final String MENSAGEM_OCUPADO = "O sistema está processando outras consultas pesadas. Tente novamente em instantes.";

    private final Semaphore semaforo;
    private final long esperaMs;
    private final int limiteMs;
    private final TransactionTemplate transacao;
    private final JdbcTemplate jdbc;

    public ConsultaPesada(PlatformTransactionManager tm, JdbcTemplate jdbc, @Value("${app.consulta-pesada.maximo-simultaneas:2}") int maximo,
                          @Value("${app.consulta-pesada.espera-ms:8000}") long esperaMs, @Value("${app.consulta-pesada.limite-ms:15000}") int limiteMs) {
        this.semaforo = new Semaphore(Math.max(1, maximo), true);
        this.esperaMs = esperaMs;
        this.limiteMs = limiteMs;
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(tm);
        this.transacao.setReadOnly(true);
    }

    public <T> T executar(Supplier<T> consulta) {
        boolean obteve;
        try {
            obteve = semaforo.tryAcquire(esperaMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SistemaOcupadoException(MENSAGEM_OCUPADO);
        }
        if (!obteve) {
            log.warn("Consulta pesada recusada: limite de consultas simultâneas atingido");
            throw new SistemaOcupadoException(MENSAGEM_OCUPADO);
        }
        try {
            return transacao.execute(status -> {
                jdbc.execute("SET LOCAL statement_timeout = " + limiteMs);
                return consulta.get();
            });
        } catch (DataAccessException e) {
            if (tempoEsgotado(e)) {
                log.warn("Consulta pesada cancelada por tempo limite ({} ms)", limiteMs);
                throw new ConsultaDemoradaException(MENSAGEM_TEMPO);
            }
            throw e;
        } finally {
            semaforo.release();
        }
    }

    // SQLSTATE 57014 = query_canceled (statement_timeout)
    static boolean tempoEsgotado(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof org.springframework.dao.QueryTimeoutException) return true;
            if (t instanceof SQLException s && "57014".equals(s.getSQLState())) return true;
        }
        return false;
    }
}
