package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// regras do pagador avulso do título a receber
class PagadorAvulsoTest {

    @Test
    void nenhumDosDoisEhRecusado() {
        assertThatThrownBy(() -> ContaReceberCalculo.validarPagador(false, null, null))
                .isInstanceOf(ItemInvalidoException.class).hasMessage("Informe o cliente ou o pagador avulso.");
        assertThatThrownBy(() -> ContaReceberCalculo.validarPagador(false, "   ", null))
                .hasMessage("Informe o cliente ou o pagador avulso.");
        assertThatThrownBy(() -> ContaReceberCalculo.validarPagador(false, null, "12345678901"))
                .hasMessage("Informe o cliente ou o pagador avulso.");
    }

    @Test
    void ambosSaoRecusados() {
        assertThatThrownBy(() -> ContaReceberCalculo.validarPagador(true, "FULANO", null))
                .hasMessage("Informe apenas o cliente ou o pagador avulso.");
        assertThatThrownBy(() -> ContaReceberCalculo.validarPagador(true, null, "12345678901"))
                .hasMessage("Informe apenas o cliente ou o pagador avulso.");
    }

    @Test
    void somenteClienteNaoGeraPagadorAvulso() {
        assertThat(ContaReceberCalculo.validarPagador(true, null, null)).isNull();
        assertThat(ContaReceberCalculo.validarPagador(true, "  ", "  ")).isNull();
    }

    @Test
    void nomeEmMaiusculasComTrimETamanho() {
        assertThat(ContaReceberCalculo.validarPagador(false, "  joão da silva  ", null).nome()).isEqualTo("JOÃO DA SILVA");
        assertThat(ContaReceberCalculo.validarPagador(false, "ab", null).nome()).isEqualTo("AB");
        assertThatThrownBy(() -> ContaReceberCalculo.validarPagador(false, "a", null)).isInstanceOf(ItemInvalidoException.class);
        assertThat(ContaReceberCalculo.validarPagador(false, "x".repeat(150), null).nome()).hasSize(150);
        assertThatThrownBy(() -> ContaReceberCalculo.validarPagador(false, "x".repeat(151), null)).isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void documentoNormalizadoParaDigitos() {
        assertThat(ContaReceberCalculo.validarPagador(false, "Loja", "123.456.789-01").documento()).isEqualTo("12345678901");
        assertThat(ContaReceberCalculo.validarPagador(false, "Loja", "12.345.678/0001-90").documento()).isEqualTo("12345678000190");
        assertThat(ContaReceberCalculo.validarPagador(false, "Loja", "").documento()).isNull();
        assertThat(ContaReceberCalculo.validarPagador(false, "Loja", null).documento()).isNull();
    }

    @Test
    void documentoInvalidoEhRecusado() {
        for (String ruim : new String[]{"123", "1234567890", "123456789012", "123456789012345", "abc45678901", "1234567890a"}) {
            assertThatThrownBy(() -> ContaReceberCalculo.validarPagador(false, "Loja", ruim))
                    .as(ruim).isInstanceOf(ItemInvalidoException.class).hasMessageContaining("11 dígitos (CPF) ou 14 dígitos (CNPJ)");
        }
    }

    @Test
    void trocaDePagadorSoSemRecebimento() {
        ContaReceberCalculo.validarTrocaDeCliente(true, new BigDecimal("0"));
        ContaReceberCalculo.validarTrocaDeCliente(false, new BigDecimal("40"));
        assertThatThrownBy(() -> ContaReceberCalculo.validarTrocaDeCliente(true, new BigDecimal("0.01")))
                .hasMessageContaining("O pagador não pode ser alterado");
    }

    @Test
    void nomeDeExibicaoUsaClienteOuAvulso() {
        assertThat(ContaReceberCalculo.pagadorExibicao("Maria", null)).isEqualTo("Maria");
        assertThat(ContaReceberCalculo.pagadorExibicao(null, "LOJA")).isEqualTo("LOJA");
    }
}
