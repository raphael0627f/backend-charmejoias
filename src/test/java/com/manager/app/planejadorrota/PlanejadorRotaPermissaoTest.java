package com.manager.app.planejadorrota;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// leitura do Planejador de rotas (lista, detalhe, xlsx) também exige PLANEJADOR_ROTA_GERENCIAR
class PlanejadorRotaPermissaoTest {

    private static final String REGRA = "hasAuthority('PLANEJADOR_ROTA_GERENCIAR')";

    @Test
    void controllerExigeAPermissaoEmTodosOsEndpoints() {
        PreAuthorize naClasse = PlanejadorRotaController.class.getAnnotation(PreAuthorize.class);
        assertThat(naClasse).isNotNull();
        assertThat(naClasse.value()).isEqualTo(REGRA);

        List<Method> endpoints = Arrays.stream(PlanejadorRotaController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
                        || m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(DeleteMapping.class))
                .toList();
        assertThat(endpoints).hasSizeGreaterThanOrEqualTo(6);
        for (Method m : endpoints) {
            // método sem anotação própria herda a da classe; se tiver, precisa ser a mesma regra
            PreAuthorize propria = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
            if (propria != null) {
                assertThat(propria.value()).as(m.getName()).isEqualTo(REGRA);
            }
        }
    }
}
