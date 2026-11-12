package com.manager.app.shared;

import com.manager.app.planejadorrota.dto.PlanejadorRotaRequestDTO;
import com.manager.app.shared.exception.ObjetoNaoEncontradoException;
import com.manager.app.shared.exception.handler.RestExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// erros do Spring MVC (validação, JSON inválido, tipo, parâmetro, rota inexistente) saem no padrão do projeto, em português
class RestExceptionHandlerValidacaoTest {

    static class Corpo {
        @NotBlank(message = "O campo Nome é obrigatório.")
        public String nome;
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
    }

    @RestController
    static class FakeController {
        @PostMapping("/fake")
        public String criar(@Valid @RequestBody Corpo corpo) { return "ok"; }

        @GetMapping("/fake/{id}")
        public String buscar(@PathVariable Long id) { return "ok"; }

        @GetMapping("/fake-param")
        public String param(@RequestParam String x) { return "ok"; }

        @GetMapping("/fake-404/{id}")
        public String naoEncontrado(@PathVariable Long id) {
            throw new ObjetoNaoEncontradoException("Pedido não encontrado. Id: " + id);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FakeController()).setControllerAdvice(new RestExceptionHandler()).build();
    }

    @Test
    void corpoComCampoInvalidoDevolveMenssageEListaDeCampos() throws Exception {
        mvc.perform(post("/fake").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.menssage").value("O campo Nome é obrigatório."))
                .andExpect(jsonPath("$.errors[0].field").value("nome"))
                .andExpect(jsonPath("$.errors[0].defaultMessage").value("O campo Nome é obrigatório."));
    }

    @Test
    void jsonInvalidoDevolve400EmPortugues() throws Exception {
        mvc.perform(post("/fake").contentType(MediaType.APPLICATION_JSON).content("{nome:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.menssage").value("O corpo da requisição está ausente ou mal formatado. Revise os dados informados."));
        mvc.perform(post("/fake").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.menssage").exists());
    }

    @Test
    void tipoDeParametroInvalido() throws Exception {
        mvc.perform(get("/fake/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.menssage").value("O valor informado para id é inválido."));
    }

    @Test
    void parametroObrigatorioAusente() throws Exception {
        mvc.perform(get("/fake-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.menssage").value("O parâmetro x é obrigatório."));
    }

    @Test
    void rotaInexistenteDevolve404() throws Exception {
        mvc.perform(get("/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.menssage").value("Recurso não encontrado."));
    }

    @Test
    void metodoNaoPermitido() throws Exception {
        mvc.perform(post("/fake/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.menssage").value("Método não permitido para este recurso."));
    }

    @Test
    void naoEncontradoNaoExibeIdTecnico() throws Exception {
        mvc.perform(get("/fake-404/55"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.menssage").value("Pedido não encontrado."));
    }

    @Test
    void semIdentificadorRemoveSoORotuloTecnico() {
        assertThat(RestExceptionHandler.semIdentificador("Perfil não encontrado. Id: 7")).isEqualTo("Perfil não encontrado.");
        assertThat(RestExceptionHandler.semIdentificador("Pedido(s) não encontrado(s). Ids: [1, 2]")).isEqualTo("Pedido(s) não encontrado(s).");
        assertThat(RestExceptionHandler.semIdentificador("Produto não encontrado. SKU: A1")).isEqualTo("Produto não encontrado.");
        assertThat(RestExceptionHandler.semIdentificador("Rota não encontrada.")).isEqualTo("Rota não encontrada.");
    }

    @Test
    void dataNasMensagensEmFormatoBrasileiro() {
        assertThat(TextoUtils.data(LocalDate.of(2031, 2, 4))).isEqualTo("04/02/2031");
    }

    @Test
    void planejadorExigeAoMenosUmCliente() {
        PlanejadorRotaRequestDTO dto = new PlanejadorRotaRequestDTO();
        dto.setNome("Rota");
        dto.setDataRota(LocalDate.of(2026, 11, 12));
        dto.setItens(new ArrayList<>());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violacoes = factory.getValidator().validate(dto);
            assertThat(violacoes).extracting(v -> v.getMessage())
                    .containsExactly("Informe ao menos um cliente na rota do planejador.");
        }
    }
}
