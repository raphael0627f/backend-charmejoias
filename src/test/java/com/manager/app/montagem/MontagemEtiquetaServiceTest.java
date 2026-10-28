package com.manager.app.montagem;

import com.manager.app.endereco.Endereco;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// regras da etiqueta da montagem (métodos estáticos puros, sem Spring e sem banco)
class MontagemEtiquetaServiceTest {

    private static Endereco endereco(Long id, boolean principal, String descricao, String logradouro, String numero,
                                     String bairro, String municipio, String complemento, String pontoReferencia,
                                     String latitude, String longitude) {
        Endereco e = new Endereco();
        e.setId(id);
        e.setPrincipal(principal);
        e.setDescricao(descricao);
        e.setLogradouro(logradouro);
        e.setNumero(numero);
        e.setBairro(bairro);
        e.setMunicipio(municipio);
        e.setComplemento(complemento);
        e.setPontoReferencia(pontoReferencia);
        e.setLatitude(latitude != null ? new BigDecimal(latitude) : null);
        e.setLongitude(longitude != null ? new BigDecimal(longitude) : null);
        return e;
    }

    private static Endereco simples(Long id, boolean principal, String logradouro, String bairro, String municipio) {
        return endereco(id, principal, null, logradouro, null, bairro, municipio, null, null, null, null);
    }

    // ---------- sobrenome ----------

    @Test
    void sobrenomeUmaPalavraEELaMesma() {
        assertThat(MontagemEtiquetaService.sobrenome("Madonna")).isEqualTo("Madonna");
    }

    @Test
    void sobrenomeDuasPalavrasIgnoraAPrimeira() {
        assertThat(MontagemEtiquetaService.sobrenome("Maria Silva")).isEqualTo("Silva");
    }

    @Test
    void sobrenomeTresPalavrasIgnoraAPrimeira() {
        assertThat(MontagemEtiquetaService.sobrenome("Maria da Silva")).isEqualTo("da Silva");
    }

    @Test
    void sobrenomeQuatroPalavrasIgnoraAPrimeira() {
        assertThat(MontagemEtiquetaService.sobrenome("Joao Pedro da Silva")).isEqualTo("Pedro da Silva");
    }

    @Test
    void sobrenomeCincoPalavrasIgnoraAsDuasPrimeiras() {
        assertThat(MontagemEtiquetaService.sobrenome("Ana Maria de Souza Lima")).isEqualTo("de Souza Lima");
    }

    @Test
    void sobrenomeSeisOuMaisPalavrasIgnoraAsDuasPrimeiras() {
        assertThat(MontagemEtiquetaService.sobrenome("Ana Maria Clara de Souza Lima")).isEqualTo("Clara de Souza Lima");
        assertThat(MontagemEtiquetaService.sobrenome("A B C D E F G H")).isEqualTo("C D E F G H");
    }

    @Test
    void sobrenomeIgnoraEspacosDuplicadosEExtremos() {
        assertThat(MontagemEtiquetaService.sobrenome("  Maria   Silva  ")).isEqualTo("Silva");
        assertThat(MontagemEtiquetaService.sobrenome("  Madonna  ")).isEqualTo("Madonna");
        assertThat(MontagemEtiquetaService.sobrenome("Ana   Maria  de   Souza  Lima")).isEqualTo("de Souza Lima");
    }

    @Test
    void sobrenomeComTabEQuebraDeLinhaTratadosComoEspaco() {
        assertThat(MontagemEtiquetaService.sobrenome("Maria\tSilva")).isEqualTo("Silva");
        assertThat(MontagemEtiquetaService.sobrenome("Maria\nSilva")).isEqualTo("Silva");
    }

    @Test
    void sobrenomeNuloOuVazioOuEmBrancoNaoEstoura() {
        assertThatCode(() -> MontagemEtiquetaService.sobrenome(null)).doesNotThrowAnyException();
        assertThat(MontagemEtiquetaService.sobrenome(null)).isEmpty();
        assertThat(MontagemEtiquetaService.sobrenome("")).isEmpty();
        assertThat(MontagemEtiquetaService.sobrenome("   ")).isEmpty();
    }

    @Test
    void sobrenomePreservaACaixa() {
        assertThat(MontagemEtiquetaService.sobrenome("MARIA DA SILVA")).isEqualTo("DA SILVA");
        assertThat(MontagemEtiquetaService.sobrenome("maria silva")).isEqualTo("silva");
        assertThat(MontagemEtiquetaService.sobrenome("João D'Ávila")).isEqualTo("D'Ávila");
    }

    // ---------- nomeContato ----------

    @Test
    void contatoCompletoNomeCelularETelefone() {
        assertThat(MontagemEtiquetaService.nomeContato("Maria", "11 99999-0000", "11 3333-0000"))
                .isEqualTo("Maria - 11 99999-0000 / 11 3333-0000");
    }

    @Test
    void contatoSoCelular() {
        assertThat(MontagemEtiquetaService.nomeContato("Maria", "11 99999-0000", null)).isEqualTo("Maria - 11 99999-0000");
    }

    @Test
    void contatoSoTelefone() {
        assertThat(MontagemEtiquetaService.nomeContato("Maria", null, "11 3333-0000")).isEqualTo("Maria - 11 3333-0000");
    }

    @Test
    void contatoSemNenhumTelefoneSoONome() {
        assertThat(MontagemEtiquetaService.nomeContato("Maria", null, null)).isEqualTo("Maria");
    }

    @Test
    void contatoComBrancosEquivaleANulo() {
        assertThat(MontagemEtiquetaService.nomeContato("Maria", "  ", "")).isEqualTo("Maria");
        assertThat(MontagemEtiquetaService.nomeContato("Maria", "", "11 3333-0000")).isEqualTo("Maria - 11 3333-0000");
        assertThat(MontagemEtiquetaService.nomeContato("Maria", "11 99999-0000", "   ")).isEqualTo("Maria - 11 99999-0000");
    }

    @Test
    void contatoSemNomeNaoDeixaSeparadorSolto() {
        assertThat(MontagemEtiquetaService.nomeContato(null, "11 99999-0000", "11 3333-0000")).isEqualTo("11 99999-0000 / 11 3333-0000");
        assertThat(MontagemEtiquetaService.nomeContato("  ", "11 99999-0000", null)).isEqualTo("11 99999-0000");
    }

    @Test
    void contatoTudoNuloOuEmBrancoEVazio() {
        assertThat(MontagemEtiquetaService.nomeContato(null, null, null)).isEmpty();
        assertThat(MontagemEtiquetaService.nomeContato(" ", " ", " ")).isEmpty();
    }

    @Test
    void contatoAparaEspacosDasPartes() {
        assertThat(MontagemEtiquetaService.nomeContato("  Maria ", " 99 ", " 33 ")).isEqualTo("Maria - 99 / 33");
    }

    // ---------- formatarData ----------

    @Test
    void dataNoFormatoDiaMesAno() {
        assertThat(MontagemEtiquetaService.formatarData(LocalDate.of(2026, 3, 5))).isEqualTo("05/03/2026");
        assertThat(MontagemEtiquetaService.formatarData(LocalDate.of(2026, 12, 31))).isEqualTo("31/12/2026");
    }

    @Test
    void dataNulaVirouVazio() {
        assertThat(MontagemEtiquetaService.formatarData(null)).isEmpty();
    }

    // ---------- enderecoEmLinha ----------

    @Test
    void enderecoCompletoSegueOFormatoDaRegra() {
        Endereco e = endereco(1L, true, "Casa", "Rua A", "10", "Centro", "Campinas", "Apto 2", "Perto da praça", "-22.9056391", "-47.0608334");
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e))
                .isEqualTo("Casa: Rua A - 10 - Centro - Campinas - Apto 2 - Perto da praça (-22.9056391, -47.0608334)");
    }

    @Test
    void enderecoSemDescricaoNaoTemPrefixo() {
        Endereco e = endereco(1L, true, null, "Rua A", "10", "Centro", "Campinas", null, null, null, null);
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).isEqualTo("Rua A - 10 - Centro - Campinas");
    }

    @Test
    void enderecoComDescricaoEmBrancoNaoTemPrefixo() {
        Endereco e = endereco(1L, true, "   ", "Rua A", "10", null, null, null, null, null, null);
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).isEqualTo("Rua A - 10");
    }

    @Test
    void enderecoOmiteParteVaziaJuntoComOSeparador() {
        Endereco e = endereco(1L, true, "Loja", "Rua A", null, "", "Campinas", "  ", "Esquina", null, null);
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).isEqualTo("Loja: Rua A - Campinas - Esquina");
    }

    @Test
    void enderecoSoComComplementoEPontoDeReferencia() {
        Endereco e = endereco(1L, false, null, null, null, null, null, "Fundos", "Ao lado do mercado", null, null);
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).isEqualTo("Fundos - Ao lado do mercado");
    }

    @Test
    void enderecoSoComLatitudeNaoTemParenteses() {
        Endereco e = endereco(1L, true, null, "Rua A", null, null, null, null, null, "-22.9", null);
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).isEqualTo("Rua A").doesNotContain("(");
    }

    @Test
    void enderecoSoComLongitudeNaoTemParenteses() {
        Endereco e = endereco(1L, true, null, "Rua A", null, null, null, null, null, null, "-47.0");
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).isEqualTo("Rua A").doesNotContain("(");
    }

    @Test
    void enderecoCoordenadasNuncaEmNotacaoCientifica() {
        Endereco e = endereco(1L, true, null, "Rua A", null, null, null, null, null, "0.0000001", "-0.0000001");
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).isEqualTo("Rua A (0.0000001, -0.0000001)");
        Endereco zero = endereco(2L, true, null, null, null, null, null, null, null, "0E-7", "0E-7");
        assertThat(MontagemEtiquetaService.enderecoEmLinha(zero)).isEqualTo("(0.0000000, 0.0000000)");
    }

    @Test
    void enderecoSoComCoordenadasFicaSoComOsParenteses() {
        Endereco e = endereco(1L, true, null, null, null, null, null, null, null, "-22.9", "-47.0");
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).isEqualTo("(-22.9, -47.0)");
    }

    @Test
    void enderecoSoComDescricaoNaoLancaExcecao() {
        Endereco e = endereco(1L, true, "Casa", null, null, null, null, null, null, null, null);
        assertThatCode(() -> MontagemEtiquetaService.enderecoEmLinha(e)).doesNotThrowAnyException();
        assertThat(MontagemEtiquetaService.enderecoEmLinha(e)).startsWith("Casa");
    }

    @Test
    void enderecoTotalmenteVazioDaLinhaVazia() {
        assertThat(MontagemEtiquetaService.enderecoEmLinha(new Endereco())).isEmpty();
    }

    // ---------- enderecosEmLinhas ----------

    @Test
    void enderecosNuloOuVazioDaTextoVazio() {
        assertThat(MontagemEtiquetaService.enderecosEmLinhas(null)).isEmpty();
        assertThat(MontagemEtiquetaService.enderecosEmLinhas(List.of())).isEmpty();
    }

    @Test
    void enderecosUmPorLinhaPrincipalPrimeiroDepoisPorId() {
        Endereco e1 = simples(1L, false, "Rua 1", null, null);
        Endereco e2 = simples(2L, true, "Rua 2", null, null);
        Endereco e3 = simples(3L, false, "Rua 3", null, null);
        // entrada fora de ordem
        assertThat(MontagemEtiquetaService.enderecosEmLinhas(List.of(e3, e1, e2)))
                .isEqualTo("Rua 2\nRua 1\nRua 3");
    }

    @Test
    void enderecosSeparadosSomentePorQuebraDeLinhaSemLinhaFinalNemVazia() {
        String texto = MontagemEtiquetaService.enderecosEmLinhas(
                List.of(simples(1L, true, "Rua 1", null, null), simples(2L, false, "Rua 2", null, null)));
        assertThat(texto).doesNotEndWith("\n").doesNotStartWith("\n").doesNotContain("\n\n");
        assertThat(texto.split("\n")).hasSize(2);
    }

    @Test
    void enderecosNaoMutaAListaRecebida() {
        List<Endereco> original = Arrays.asList(simples(3L, false, "Rua 3", null, null), simples(1L, true, "Rua 1", null, null));
        MontagemEtiquetaService.enderecosEmLinhas(original);
        assertThat(original).extracting(Endereco::getId).containsExactly(3L, 1L);
    }

    @Test
    void enderecosSemPrincipalFicamOrdenadosPorId() {
        assertThat(MontagemEtiquetaService.enderecosEmLinhas(
                List.of(simples(5L, false, "Rua 5", null, null), simples(2L, false, "Rua 2", null, null))))
                .isEqualTo("Rua 2\nRua 5");
    }

    @Test
    void enderecosSemIdVaoParaOFinalSemEstourar() {
        Endereco semId = simples(null, false, "Rua sem id", null, null);
        Endereco comId = simples(1L, false, "Rua 1", null, null);
        assertThatCode(() -> MontagemEtiquetaService.enderecosEmLinhas(List.of(semId, comId))).doesNotThrowAnyException();
        assertThat(MontagemEtiquetaService.enderecosEmLinhas(List.of(semId, comId))).isEqualTo("Rua 1\nRua sem id");
    }

    @Test
    void enderecosVaziosNaoGeramLinhaEmBranco() {
        assertThat(MontagemEtiquetaService.enderecosEmLinhas(
                List.of(new Endereco(), simples(2L, false, "Rua 2", null, null))))
                .isEqualTo("Rua 2");
    }

    // ---------- enderecoPrincipal ----------

    @Test
    void principalEOMarcadoComoPrincipal() {
        Endereco e1 = simples(1L, false, "Rua 1", null, null);
        Endereco e2 = simples(2L, true, "Rua 2", null, null);
        assertThat(MontagemEtiquetaService.enderecoPrincipal(List.of(e1, e2))).isSameAs(e2);
    }

    @Test
    void semPrincipalUsaOPrimeiroDaLista() {
        Endereco e1 = simples(1L, false, "Rua 1", null, null);
        Endereco e2 = simples(2L, false, "Rua 2", null, null);
        assertThat(MontagemEtiquetaService.enderecoPrincipal(List.of(e1, e2))).isSameAs(e1);
    }

    @Test
    void semEnderecosPrincipalENulo() {
        assertThat(MontagemEtiquetaService.enderecoPrincipal(null)).isNull();
        assertThat(MontagemEtiquetaService.enderecoPrincipal(List.of())).isNull();
    }

    // ---------- nomeBairroCidade ----------

    @Test
    void nomeBairroCidadeUsaOEnderecoPrincipal() {
        Endereco principal = simples(2L, true, "Rua 2", "Centro", "Campinas");
        assertThat(MontagemEtiquetaService.nomeBairroCidade("Maria", principal)).isEqualTo("Maria - Centro - Campinas");
    }

    @Test
    void nomeBairroCidadeSemEnderecoSoONome() {
        assertThat(MontagemEtiquetaService.nomeBairroCidade("Maria", null)).isEqualTo("Maria");
    }

    @Test
    void nomeBairroCidadeOmiteBairroOuCidadeVazios() {
        assertThat(MontagemEtiquetaService.nomeBairroCidade("Maria", simples(1L, true, "R", null, "Campinas"))).isEqualTo("Maria - Campinas");
        assertThat(MontagemEtiquetaService.nomeBairroCidade("Maria", simples(1L, true, "R", "Centro", " "))).isEqualTo("Maria - Centro");
        assertThat(MontagemEtiquetaService.nomeBairroCidade("Maria", simples(1L, true, "R", null, null))).isEqualTo("Maria");
    }

    @Test
    void nomeBairroCidadeSemNomeNaoDeixaSeparadorSolto() {
        assertThat(MontagemEtiquetaService.nomeBairroCidade(null, simples(1L, true, "R", "Centro", "Campinas"))).isEqualTo("Centro - Campinas");
    }

    @Test
    void nomeBairroCidadeCombinadoComEnderecoPrincipalDaLista() {
        List<Endereco> lista = List.of(simples(1L, false, "Rua 1", "Vila", "Valinhos"), simples(2L, true, "Rua 2", "Centro", "Campinas"));
        assertThat(MontagemEtiquetaService.nomeBairroCidade("Maria", MontagemEtiquetaService.enderecoPrincipal(lista)))
                .isEqualTo("Maria - Centro - Campinas");
    }
}
