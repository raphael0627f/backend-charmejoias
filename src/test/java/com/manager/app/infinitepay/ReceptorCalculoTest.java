package com.manager.app.infinitepay;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReceptorCalculoTest {

    private static final String SEGREDO = "abcdefghijklmnopqrstuvwxyz012345-_ABCDEF";

    @Test
    void aceitaHttpsDeDominioProprioENormalizaBarraFinal() {
        assertEquals("https://pagamentos.charmejoias.com.br", ReceptorCalculo.validarUrl("https://pagamentos.charmejoias.com.br/", false));
        assertEquals("https://pagamentos.charmejoias.com.br", ReceptorCalculo.validarUrl("  HTTPS://pagamentos.charmejoias.com.br  ", false).toLowerCase());
    }

    @Test
    void recusaWorkersDevHttpLocalCaminhoECredenciais() {
        for (String url : new String[]{"https://charme-pagamentos.conta.workers.dev", "http://pagamentos.charmejoias.com.br", "https://localhost",
                "https://127.0.0.1", "https://192.168.0.10", "https://10.0.0.5", "https://172.16.3.4", "https://servidor",
                "https://pagamentos.charmejoias.com.br/w", "https://pagamentos.charmejoias.com.br?x=1", "https://u:s@pagamentos.charmejoias.com.br",
                "ftp://pagamentos.charmejoias.com.br", "", "  ", "isto nao e url"}) {
            assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarUrl(url, false), url);
        }
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarUrl(null, false));
    }

    @Test
    void urlLocalHttpSoComAPropriedadeDeTeste() {
        assertEquals("http://127.0.0.1:9100", ReceptorCalculo.validarUrl("http://127.0.0.1:9100/", true));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarUrl("ftp://127.0.0.1", true));
    }

    @Test
    void segredoExigeTamanhoMinimoECaracteresSeguros() {
        assertEquals(SEGREDO, ReceptorCalculo.validarSegredo("O segredo", "  " + SEGREDO + " "));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarSegredo("O segredo", "curto"));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarSegredo("O segredo", SEGREDO + "/"));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarSegredo("O segredo", SEGREDO + " x"));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarSegredo("O segredo", "a".repeat(201)));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarSegredo("O segredo", null));
    }

    @Test
    void intervaloEntre1e60() {
        assertEquals(2, ReceptorCalculo.validarIntervalo(2));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarIntervalo(0));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarIntervalo(61));
        assertThrows(ItemInvalidoException.class, () -> ReceptorCalculo.validarIntervalo(null));
    }

    @Test
    void webhookMontaUrlComTokenEEventos() {
        Map<String, Object> w = ReceptorCalculo.webhook("https://pagamentos.charmejoias.com.br", SEGREDO);
        assertEquals("https://pagamentos.charmejoias.com.br/w/" + SEGREDO, w.get("url"));
        assertTrue(((java.util.List<?>) w.get("events")).contains("paid"));
        assertFalse(((java.util.List<?>) w.get("events")).contains("created"));
    }
}
