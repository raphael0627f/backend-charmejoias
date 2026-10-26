package com.manager.app.usuario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.manager.app.usuario.dto.UsuarioResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// regressão: GET /usuarios devolvia JSON truncado porque Usuario.perfil -> Perfil.updatedBy -> Usuario
// (o próprio) -> perfil ... formava um ciclo. Sem Spring e sem banco.
class UsuarioSerializacaoCicloTest {

    private ObjectMapper objectMapper;
    private UsuarioMapper usuarioMapper;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();

        UsuarioResumoMapper resumoMapper = new UsuarioResumoMapperImpl();
        PerfilMapperImpl perfilMapper = new PerfilMapperImpl();
        ReflectionTestUtils.setField(perfilMapper, "usuarioResumoMapper", resumoMapper);
        UsuarioMapperImpl impl = new UsuarioMapperImpl();
        ReflectionTestUtils.setField(impl, "usuarioResumoMapper", resumoMapper);
        ReflectionTestUtils.setField(impl, "perfilMapper", perfilMapper);
        ReflectionTestUtils.setField(impl, "enderecoMapper", new com.manager.app.endereco.EnderecoMapperImpl());
        usuarioMapper = impl;

        // ciclo: o perfil foi salvo pelo próprio admin, e o admin tem esse perfil
        Perfil perfil = new Perfil();
        perfil.setId(2L);
        perfil.setNome("ADMIN");
        perfil.setPermissoes(EnumSet.allOf(Permissao.class));
        perfil.setCreatedAt(LocalDateTime.of(2026, 9, 13, 20, 1));
        perfil.setUpdatedAt(LocalDateTime.of(2026, 10, 26, 2, 30));

        admin = new Usuario();
        admin.setId(UUID.fromString("c5cef6c3-9daa-4423-bbb3-4dba230a5185"));
        admin.setUsuario("admin");
        admin.setPrimeiroNome("RAPHAEL");
        admin.setSenha("hash-que-nao-pode-vazar");
        admin.setAtivo(true);
        admin.setPerfil(perfil);
        admin.setCreatedBy(admin);
        admin.setUpdatedBy(admin);

        perfil.setCreatedBy(admin);
        perfil.setUpdatedBy(admin);
    }

    @Test
    void responseDtoComCicloGeraJsonValidoEFinito() throws Exception {
        UsuarioResponseDTO dto = usuarioMapper.toResponseDTO(admin);

        String json = objectMapper.writeValueAsString(dto);

        JsonNode raiz = objectMapper.readTree(json);
        assertThat(json.length()).isLessThan(5_000);
        assertThat(raiz.get("usuario").asText()).isEqualTo("admin");
        assertThat(raiz.get("perfil").get("nome").asText()).isEqualTo("ADMIN");
        assertThat(raiz.get("perfil").get("permissoes")).isNotEmpty();
        assertThat(profundidade(raiz)).isLessThan(10);
    }

    @Test
    void responseDtoNaoExpoeSenhaNemUsuarioInteiroEmAuditoria() throws Exception {
        String json = objectMapper.writeValueAsString(usuarioMapper.toResponseDTO(admin));

        assertThat(json).doesNotContain("senha").doesNotContain("hash-que-nao-pode-vazar");
        JsonNode updatedBy = objectMapper.readTree(json).get("updatedBy");
        assertThat(updatedBy.get("usuario").asText()).isEqualTo("admin");
        assertThat(updatedBy.has("perfil")).isFalse();
    }

    @Test
    void entidadeUsuarioComCicloNaoRecursa() throws Exception {
        String json = objectMapper.writeValueAsString(admin);

        JsonNode raiz = objectMapper.readTree(json);
        assertThat(raiz.get("perfil").get("nome").asText()).isEqualTo("ADMIN");
        assertThat(raiz.has("createdBy")).isFalse();
        assertThat(raiz.has("updatedBy")).isFalse();
        assertThat(raiz.has("senha")).isFalse();
        assertThat(json.length()).isLessThan(5_000);
    }

    @Test
    void entidadePerfilComCicloNaoRecursa() throws Exception {
        String json = objectMapper.writeValueAsString(admin.getPerfil());

        JsonNode raiz = objectMapper.readTree(json);
        assertThat(raiz.get("nome").asText()).isEqualTo("ADMIN");
        assertThat(raiz.has("createdBy")).isFalse();
        assertThat(raiz.has("updatedBy")).isFalse();
        assertThat(json.length()).isLessThan(5_000);
    }

    private int profundidade(JsonNode no) {
        int max = 0;
        for (JsonNode filho : no) {
            max = Math.max(max, profundidade(filho));
        }
        return max + 1;
    }
}
