package com.manager.app.infinitepay;

import com.manager.app.infinitepay.dto.InfinitePayDTOs;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// receptor simulado em servidor HTTP local: NUNCA chama a Cloudflare nem a InfinitePay reais
class ReceptorClientTest {

    private static final String SEGREDO = "segredoErpSegredoErpSegredoErpSegredo9876";

    private HttpServer servidor;
    private final AtomicReference<String> chamada = new AtomicReference<>();
    private final AtomicReference<String> autorizacao = new AtomicReference<>();
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String resposta = "{}";
    private volatile long atrasoMs = 0;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            chamada.set(troca.getRequestMethod() + " " + troca.getRequestURI());
            autorizacao.set(troca.getRequestHeaders().getFirst("Authorization"));
            corpoRecebido.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (atrasoMs > 0) {
                try {
                    Thread.sleep(atrasoMs);
                } catch (InterruptedException ignorado) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private ReceptorClient cliente(long timeoutSegundos) {
        InfinitePayAutomaticoService automatico = mock(InfinitePayAutomaticoService.class);
        when(automatico.receptor()).thenReturn(new InfinitePayAutomaticoService.Receptor("http://127.0.0.1:" + servidor.getAddress().getPort(), SEGREDO));
        return new ReceptorClient(automatico, JsonMapper.builder().build(), timeoutSegundos);
    }

    @Test
    void buscaAFilaComSegredoNoCabecalhoELimite() {
        resposta = "{\"itens\":[{\"id\":7,\"recebidoEm\":\"2026-11-23T10:00:00Z\",\"evento\":\"paid\",\"orderNsu\":\"CR-1-abc\",\"transactionNsu\":\"T1\","
                + "\"invoiceSlug\":\"S1\",\"corpo\":\"{\\\"amount\\\":1500}\"},{\"id\":8,\"corpo\":{\"x\":1}},{\"semId\":true}]}";
        List<ReceptorClient.ItemFila> fila = cliente(5).buscarFila(30);
        assertEquals("GET /fila?limite=30", chamada.get());
        assertEquals("Bearer " + SEGREDO, autorizacao.get());
        assertEquals(2, fila.size());
        assertEquals(7L, fila.get(0).id());
        assertEquals("CR-1-abc", fila.get(0).orderNsu());
        assertEquals("T1", fila.get(0).transactionNsu());
        assertEquals("{\"amount\":1500}", fila.get(0).corpo());
        assertEquals("{\"x\":1}", fila.get(1).corpo());
        assertNull(fila.get(1).transactionNsu());
    }

    @Test
    void aceitaFilaComoArrayELimiteEFicaEntre1e200() {
        resposta = "[{\"id\":1}]";
        assertEquals(1, cliente(5).buscarFila(5000).size());
        assertEquals("GET /fila?limite=200", chamada.get());
        cliente(5).buscarFila(0);
        assertEquals("GET /fila?limite=1", chamada.get());
    }

    @Test
    void filaVaziaOuSemItens() {
        resposta = "{}";
        assertTrue(cliente(5).buscarFila(50).isEmpty());
    }

    @Test
    void ackEnviaOsIdsEIgnoraListaVazia() {
        cliente(5).ack(List.of());
        assertNull(chamada.get());
        cliente(5).ack(List.of(1L, 2L));
        assertEquals("POST /fila/ack", chamada.get());
        assertEquals("{\"ids\":[1,2]}", corpoRecebido.get());
        assertEquals("Bearer " + SEGREDO, autorizacao.get());
    }

    @Test
    void saudeDevolveOStatusDoReceptor() {
        resposta = "{\"ok\":true,\"pendentes\":3,\"maisAntigoEm\":\"2026-11-23T08:00:00Z\",\"versao\":\"1.0\"}";
        InfinitePayDTOs.ReceptorSaude s = cliente(5).saude();
        assertEquals("GET /saude", chamada.get());
        assertTrue(s.ok());
        assertEquals(3L, s.pendentes());
        assertEquals("1.0", s.versao());
    }

    @Test
    void erro401ViraMensagemFormalSemVazarOSegredo() {
        status = 401;
        resposta = "{\"erro\":\"" + SEGREDO + "\"}";
        InfinitePayApiException e = assertThrows(InfinitePayApiException.class, () -> cliente(5).buscarFila(10));
        assertTrue(e.getMessage().contains("segredo"));
        assertFalse(e.getMessage().contains(SEGREDO));
    }

    @Test
    void erro5xxERespostaInvalidaViramMensagemFormal() {
        status = 503;
        InfinitePayApiException e = assertThrows(InfinitePayApiException.class, () -> cliente(5).saude());
        assertFalse(e.getMessage().contains(SEGREDO));
        status = 200;
        resposta = "isto nao e json";
        assertThrows(InfinitePayApiException.class, () -> cliente(5).saude());
    }

    @Test
    void timeoutViraIndisponibilidade() {
        atrasoMs = 3000;
        InfinitePayApiException e = assertThrows(InfinitePayApiException.class, () -> cliente(1).saude());
        assertTrue(e.getMessage().contains("indisponível"));
    }

    @Test
    void receptorForaDoArViraIndisponibilidade() {
        servidor.stop(0);
        assertThrows(InfinitePayApiException.class, () -> cliente(2).saude());
    }
}
