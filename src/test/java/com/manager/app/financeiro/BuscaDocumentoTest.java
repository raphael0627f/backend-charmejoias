package com.manager.app.financeiro;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuscaDocumentoTest {

    @Test
    void cpfMascaradoViraSoDigitos() {
        assertThat(BuscaDocumento.digitos("111.444.777-35")).isEqualTo("11144477735");
        assertThat(BuscaDocumento.digitos("  111.444.777-35 ")).isEqualTo("11144477735");
        assertThat(BuscaDocumento.padrao("111.444.777-35")).isEqualTo("%11144477735%");
    }

    @Test
    void cnpjMascaradoViraSoDigitos() {
        assertThat(BuscaDocumento.digitos("12.345.678/0001-95")).isEqualTo("12345678000195");
    }

    @Test
    void entradaSemMascaraOuComTextoNaoGeraBuscaPorDigitos() {
        assertThat(BuscaDocumento.digitos("11144477735")).isNull();
        assertThat(BuscaDocumento.digitos("Maria 111.444")).isNull();
        assertThat(BuscaDocumento.digitos("maria-alice")).isNull();
        assertThat(BuscaDocumento.digitos("---")).isNull();
        assertThat(BuscaDocumento.digitos(" ")).isNull();
        assertThat(BuscaDocumento.digitos(null)).isNull();
    }
}
