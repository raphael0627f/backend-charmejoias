package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoriaReservadaTest {

    private static CategoriaDespesa cat(String nome, CategoriaDespesa pai) {
        CategoriaDespesa c = new CategoriaDespesa();
        c.setNome(nome);
        c.setPai(pai);
        return c;
    }

    @Test
    void grupoReservadoESeusSubgruposSaoReservados() {
        CategoriaDespesa grupo = cat("Devoluções a clientes", null);
        assertThat(ContaPagarService.categoriaReservada(grupo)).isTrue();
        assertThat(ContaPagarService.categoriaReservada(cat("devoluções A CLIENTES", null))).isTrue();
        assertThat(ContaPagarService.categoriaReservada(cat("Qualquer", grupo))).isTrue();
    }

    @Test
    void outrasCategoriasNaoSaoReservadas() {
        CategoriaDespesa outro = cat("Aluguel", null);
        assertThat(ContaPagarService.categoriaReservada(outro)).isFalse();
        assertThat(ContaPagarService.categoriaReservada(cat("Devoluções a clientes", outro))).isFalse();
        assertThat(ContaPagarService.categoriaReservada(null)).isFalse();
    }

    @Test
    void usoManualEhRecusadoComMensagemFormal() {
        assertThatThrownBy(() -> ContaPagarService.exigirCategoriaLivre(cat("Devoluções a clientes", null)))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("reservada");
        ContaPagarService.exigirCategoriaLivre(cat("Aluguel", null));
    }
}
