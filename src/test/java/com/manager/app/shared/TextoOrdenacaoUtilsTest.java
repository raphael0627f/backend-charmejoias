package com.manager.app.shared;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextoOrdenacaoUtilsTest {

    @Test
    void normalizaHifenApostrofoEPontuacaoComoEspaco() {
        assertThat(TextoUtils.normalizarBusca("Mogi-Guaçu")).isEqualTo("mogi guacu");
        assertThat(TextoUtils.normalizarBusca("Mogi Guaçu")).isEqualTo("mogi guacu");
        assertThat(TextoUtils.normalizarBusca("Alta Floresta D'Oeste")).isEqualTo("alta floresta d oeste");
        assertThat(TextoUtils.normalizarBusca("  São   José\u0000 ")).isEqualTo("sao jose");
        assertThat(TextoUtils.normalizarBusca("--")).isEmpty();
    }

    @Test
    void textoComNulOuMuitoLongoEhRecusado() {
        assertThatThrownBy(() -> TextoUtils.validarTexto("a".repeat(256), "Nome", 255))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("Nome deve ter no máximo 255 caracteres");
        assertThatThrownBy(() -> TextoUtils.validarTexto("a\u0000b", "Nome", 255))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("caracteres inválidos");
        assertThatCode(() -> TextoUtils.validarTexto("a".repeat(255), "Nome", 255)).doesNotThrowAnyException();
        assertThatCode(() -> TextoUtils.validarTexto(null, "Nome", 255)).doesNotThrowAnyException();
    }

    @Test
    void ordenacaoComCampoInexistenteVira400ComCamposPermitidos() {
        Set<String> permitidos = Set.of("nome", "uf");
        assertThatThrownBy(() -> OrdenacaoUtils.validar(PageRequest.of(0, 10, Sort.by("xpto")), permitidos, Set.of()))
                .isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("xpto").hasMessageContaining("nome, uf");
        assertThatCode(() -> OrdenacaoUtils.validar(PageRequest.of(0, 10, Sort.by("nome")), permitidos, Set.of())).doesNotThrowAnyException();
        assertThatCode(() -> OrdenacaoUtils.validar(PageRequest.of(0, 10, Sort.by("interno")), permitidos, Set.of("interno"))).doesNotThrowAnyException();
        assertThatCode(() -> OrdenacaoUtils.validar(PageRequest.of(0, 10), permitidos, Set.of())).doesNotThrowAnyException();
    }
}
