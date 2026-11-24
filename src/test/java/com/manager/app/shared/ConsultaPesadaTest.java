package com.manager.app.shared;

import com.manager.app.shared.exception.ConsultaDemoradaException;
import com.manager.app.shared.exception.SistemaOcupadoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// salvaguarda das consultas pesadas: limite simultâneo, tempo limite no banco e liberação da vaga
class ConsultaPesadaTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private ConsultaPesada guarda(int maximo, long esperaMs) {
        return new ConsultaPesada(mock(PlatformTransactionManager.class), jdbc, maximo, esperaMs, 15000);
    }

    @Test
    void definePrazoNoBancoEDevolveOResultado() {
        assertEquals("ok", guarda(2, 100).executar(() -> "ok"));
        verify(jdbc).execute("SET LOCAL statement_timeout = 15000");
    }

    @Test
    void estouroDeTempoNoBancoViraConsultaDemorada() {
        ConsultaDemoradaException e = assertThrows(ConsultaDemoradaException.class, () -> guarda(2, 100).executar(() -> {
            throw new QueryTimeoutException("cancelada");
        }));
        assertEquals("A consulta excedeu o tempo limite. Reduza o período.", e.getMessage());
    }

    @Test
    void sqlstate57014TambemEhTempoEsgotado() {
        assertTrue(ConsultaPesada.tempoEsgotado(new DataAccessResourceFailureException("x", new SQLException("cancelado", "57014"))));
        assertFalse(ConsultaPesada.tempoEsgotado(new DataAccessResourceFailureException("x", new SQLException("outro", "42P01"))));
    }

    @Test
    void outrosErrosDeBancoPassamIntactos() {
        assertThrows(DataAccessResourceFailureException.class, () -> guarda(2, 100).executar(() -> {
            throw new DataAccessResourceFailureException("falha");
        }));
    }

    @Test
    void alemDoLimiteSimultaneoRecebeSistemaOcupadoELiberaAVagaDepois() throws Exception {
        ConsultaPesada g = guarda(1, 150);
        CountDownLatch dentro = new CountDownLatch(1), solta = new CountDownLatch(1);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        Thread lenta = new Thread(() -> {
            try {
                g.executar(() -> {
                    dentro.countDown();
                    try {
                        solta.await();
                    } catch (InterruptedException ignored) {
                    }
                    return 1;
                });
            } catch (Throwable t) {
                erro.set(t);
            }
        });
        lenta.start();
        assertTrue(dentro.await(2, java.util.concurrent.TimeUnit.SECONDS));
        SistemaOcupadoException e = assertThrows(SistemaOcupadoException.class, () -> g.executar(() -> 2));
        assertEquals("O sistema está processando outras consultas pesadas. Tente novamente em instantes.", e.getMessage());
        solta.countDown();
        lenta.join(2000);
        assertNull(erro.get());
        assertEquals(3, g.executar(() -> 3)); // a vaga foi devolvida
    }

    @Test
    void vagaEhDevolvidaMesmoQuandoAConsultaFalha() {
        ConsultaPesada g = guarda(1, 100);
        assertThrows(IllegalStateException.class, () -> g.executar(() -> {
            throw new IllegalStateException("x");
        }));
        assertEquals("depois", g.executar(() -> "depois"));
    }
}
