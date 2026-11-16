package com.manager.app.infinitepay;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// servidor HTTP simulado local: NUNCA chama a InfinitePay real
class InfinitePayClientTest {

    private static final String CHAVE = "inchk_segredo_de_teste_1234";

    private HttpServer servidor;
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private final AtomicReference<String> chaveRecebida = new AtomicReference<>();
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String resposta = "{\"url\":\"https://checkout.simulado/abc\"}";
    private volatile long atrasoMs = 0;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            caminho.set(troca.getRequestMethod() + " " + troca.getRequestURI().getPath());
            chaveRecebida.set(troca.getRequestHeaders().getFirst("X-Api-Key"));
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

    private InfinitePayClient cliente(long timeoutSegundos) {
        return new InfinitePayClient("http://127.0.0.1:" + servidor.getAddress().getPort(), timeoutSegundos, JsonMapper.builder().build());
    }

    @Test
    void criaLinkEnviandoChaveNoCabecalho() {
        InfinitePayClient.LinkCriado criado = cliente(5).criarLink(CHAVE, Map.of("handle", "tag", "order_nsu", "CR-1-x"));
        assertEquals("https://checkout.simulado/abc", criado.url());
        assertEquals("POST /links", caminho.get());
        assertEquals(CHAVE, chaveRecebida.get());
        assertTrue(corpoRecebido.get().contains("\"order_nsu\":\"CR-1-x\""));
        assertFalse(corpoRecebido.get().contains(CHAVE), "a chave só vai no cabeçalho");
        assertEquals(resposta, criado.respostaBruta());
    }

    @Test
    void aceitaUrlEmVariantesEAninhada() {
        resposta = "{\"data\":{\"checkout_url\":\"https://checkout.simulado/zzz\"}}";
        assertEquals("https://checkout.simulado/zzz", cliente(5).criarLink(CHAVE, Map.of("a", 1)).url());
    }

    @Test
    void respostaSemUrlViraErroFormal() {
        resposta = "{\"mensagem\":\"criado\"}";
        InfinitePayApiException e = assertThrows(InfinitePayApiException.class, () -> cliente(5).criarLink(CHAVE, Map.of("a", 1)));
        assertEquals("A InfinitePay não devolveu o link de pagamento.", e.getMessage());
    }

    @Test
    void errosViramMensagensFormaisSemVazarChaveNemCorpo() {
        resposta = "{\"erro\":\"detalhe interno sigiloso " + CHAVE + "\"}";
        int[] statuses = {400, 401, 403, 422, 500, 503};
        String[] esperado = {"recusou", "credenciais", "credenciais", "recusou", "indisponível", "indisponível"};
        for (int i = 0; i < statuses.length; i++) {
            status = statuses[i];
            InfinitePayApiException e = assertThrows(InfinitePayApiException.class, () -> cliente(5).criarLink(CHAVE, Map.of("a", 1)), "HTTP " + statuses[i]);
            assertTrue(e.getMessage().contains(esperado[i]), "HTTP " + statuses[i] + ": " + e.getMessage());
            assertFalse(e.getMessage().contains(CHAVE));
            assertFalse(e.getMessage().contains("sigiloso"));
        }
    }

    @Test
    void tempoEsgotadoViraIndisponibilidade() {
        atrasoMs = 3000;
        InfinitePayApiException e = assertThrows(InfinitePayApiException.class, () -> cliente(1).criarLink(CHAVE, Map.of("a", 1)));
        assertTrue(e.getMessage().contains("indisponível"));
        assertFalse(e.getMessage().contains(CHAVE));
    }

    @Test
    void servidorForaDoArViraIndisponibilidade() {
        InfinitePayClient c = cliente(2);
        servidor.stop(0);
        InfinitePayApiException e = assertThrows(InfinitePayApiException.class, () -> c.criarLink(CHAVE, Map.of("a", 1)));
        assertTrue(e.getMessage().contains("indisponível"));
    }
}
