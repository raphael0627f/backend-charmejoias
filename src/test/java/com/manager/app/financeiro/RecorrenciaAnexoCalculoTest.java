package com.manager.app.financeiro;

import com.manager.app.shared.exception.ArquivoMuitoGrandeException;
import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.manager.app.financeiro.PeriodicidadeRecorrencia.ANUAL;
import static com.manager.app.financeiro.PeriodicidadeRecorrencia.MENSAL;
import static com.manager.app.financeiro.PeriodicidadeRecorrencia.SEMANAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecorrenciaAnexoCalculoTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static ContaPagar titulo(long id, SituacaoContaPagar situacao, String vencimento) {
        ContaPagar c = new ContaPagar();
        c.setId(id);
        c.setSituacao(situacao);
        c.setVencimento(d(vencimento));
        c.setValor(new BigDecimal("100"));
        return c;
    }

    // ---------- datas da recorrência ----------

    @Test
    void mensalNoDia31AjustaFimDeMesSemDerivar() {
        List<LocalDate> datas = RecorrenciaCalculo.datas(MENSAL, d("2027-01-31"), d("2027-06-30"));
        assertThat(datas).containsExactly(d("2027-01-31"), d("2027-02-28"), d("2027-03-31"), d("2027-04-30"), d("2027-05-31"), d("2027-06-30"));
    }

    @Test
    void mensalEmAnoBissextoUsa29DeFevereiro() {
        assertThat(RecorrenciaCalculo.datas(MENSAL, d("2028-01-31"), d("2028-03-31"))).containsExactly(d("2028-01-31"), d("2028-02-29"), d("2028-03-31"));
    }

    @Test
    void anualEm29DeFevereiroCaiEm28NosAnosNaoBissextos() {
        List<LocalDate> datas = RecorrenciaCalculo.datas(ANUAL, d("2028-02-29"), d("2032-12-31"));
        assertThat(datas).containsExactly(d("2028-02-29"), d("2029-02-28"), d("2030-02-28"), d("2031-02-28"), d("2032-02-29"));
    }

    @Test
    void semanalGeraACadaSeteDiasIncluindoOLimite() {
        assertThat(RecorrenciaCalculo.datas(SEMANAL, d("2026-11-02"), d("2026-11-23"))).containsExactly(d("2026-11-02"), d("2026-11-09"), d("2026-11-16"), d("2026-11-23"));
    }

    @Test
    void janelaEhDeDozeMesesOuADataFim() {
        assertThat(RecorrenciaCalculo.limiteJanela(d("2026-11-14"), null)).isEqualTo(d("2027-11-14"));
        assertThat(RecorrenciaCalculo.limiteJanela(d("2026-11-14"), d("2027-03-01"))).isEqualTo(d("2027-03-01"));
        assertThat(RecorrenciaCalculo.limiteJanela(d("2026-11-14"), d("2030-01-01"))).isEqualTo(d("2027-11-14"));
    }

    @Test
    void criacaoMensalGeraDozeOcorrenciasNaJanelaDeDozeMeses() {
        List<LocalDate> datas = RecorrenciaCalculo.validarCriacao(MENSAL, d("2026-12-05"), null, d("2026-11-14"));
        assertThat(datas).hasSize(12).startsWith(d("2026-12-05")).endsWith(d("2027-11-05"));
    }

    @Test
    void criacaoComDataFimParaNaDataFim() {
        List<LocalDate> datas = RecorrenciaCalculo.validarCriacao(MENSAL, d("2026-12-05"), d("2027-02-10"), d("2026-11-14"));
        assertThat(datas).containsExactly(d("2026-12-05"), d("2027-01-05"), d("2027-02-05"));
    }

    @Test
    void primeiroVencimentoAlemDaJanelaGeraAoMenosAPrimeiraOcorrencia() {
        assertThat(RecorrenciaCalculo.validarCriacao(ANUAL, d("2028-02-29"), null, d("2026-11-14"))).containsExactly(d("2028-02-29"));
    }

    @Test
    void criacaoRecusaDadosInvalidos() {
        assertThatThrownBy(() -> RecorrenciaCalculo.validarCriacao(null, d("2026-12-05"), null, d("2026-11-14"))).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> RecorrenciaCalculo.validarCriacao(MENSAL, null, null, d("2026-11-14"))).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> RecorrenciaCalculo.validarCriacao(MENSAL, d("2026-12-05"), d("2026-12-04"), d("2026-11-14")))
                .hasMessageContaining("anterior ao primeiro vencimento");
        // semanal desde 2010 passa de 520 ocorrências
        assertThatThrownBy(() -> RecorrenciaCalculo.validarCriacao(SEMANAL, d("2010-01-04"), null, d("2026-11-14"))).hasMessageContaining("mais de 520");
    }

    // ---------- idempotência ----------

    @Test
    void faltantesSoDevolveDatasAindaNaoGeradas() {
        List<LocalDate> existentes = List.of(d("2027-01-31"), d("2027-02-28"), d("2027-04-30"));
        assertThat(RecorrenciaCalculo.faltantes(MENSAL, d("2027-01-31"), d("2027-06-30"), existentes)).containsExactly(d("2027-03-31"), d("2027-05-31"), d("2027-06-30"));
    }

    @Test
    void rodarDuasVezesNaoGeraNada() {
        List<LocalDate> primeira = RecorrenciaCalculo.faltantes(MENSAL, d("2026-12-05"), d("2027-11-14"), List.of());
        assertThat(primeira).hasSize(12);
        assertThat(RecorrenciaCalculo.faltantes(MENSAL, d("2026-12-05"), d("2027-11-14"), primeira)).isEmpty();
    }

    @Test
    void passarDoTempoSoAcrescentaAsNovasDatasDaJanela() {
        List<LocalDate> geradas = RecorrenciaCalculo.datas(MENSAL, d("2026-12-05"), d("2027-11-14"));
        List<LocalDate> novas = RecorrenciaCalculo.faltantes(MENSAL, d("2026-12-05"), RecorrenciaCalculo.limiteJanela(d("2026-12-20"), null), geradas);
        assertThat(novas).containsExactly(d("2027-12-05"));
    }

    @Test
    void dataOriginalAusenteDeixaDeSerFaltanteSeFoiCanceladaMasNaoSeFoiApagada() {
        // a comparação usa a data original da ocorrência: um título remarcado ou cancelado continua contando como gerado
        assertThat(RecorrenciaCalculo.faltantes(SEMANAL, d("2026-11-02"), d("2026-11-16"), Set.of(d("2026-11-09")))).containsExactly(d("2026-11-02"), d("2026-11-16"));
    }

    @Test
    void dataFimEncerraAGeracao() {
        assertThat(RecorrenciaCalculo.faltantes(MENSAL, d("2026-12-05"), RecorrenciaCalculo.limiteJanela(d("2030-01-01"), d("2027-02-10")), List.of(d("2026-12-05"))))
                .containsExactly(d("2027-01-05"), d("2027-02-05"));
    }

    // ---------- seleção das ocorrências afetadas ----------

    @Test
    void reajusteAtingeSoFuturasAbertasSemBaixa() {
        ContaPagar passada = titulo(1, SituacaoContaPagar.ABERTO, "2026-12-05");
        ContaPagar aberta = titulo(2, SituacaoContaPagar.ABERTO, "2027-01-05");
        ContaPagar comBaixa = titulo(3, SituacaoContaPagar.ABERTO, "2027-02-05");
        ContaPagar parcial = titulo(4, SituacaoContaPagar.PAGO_PARCIAL, "2027-03-05");
        ContaPagar paga = titulo(5, SituacaoContaPagar.PAGO, "2027-04-05");
        ContaPagar cancelada = titulo(6, SituacaoContaPagar.CANCELADO, "2027-05-05");
        ContaPagar noLimite = titulo(7, SituacaoContaPagar.ABERTO, "2027-01-01");
        List<ContaPagar> alvo = RecorrenciaCalculo.selecionarReajustaveis(List.of(passada, aberta, comBaixa, parcial, paga, cancelada, noLimite), d("2027-01-01"), Set.of(3L));
        assertThat(alvo).containsExactlyInAnyOrder(aberta, noLimite);
    }

    @Test
    void encerramentoAtingeSoFuturasAbertasSemBaixaAPartirDeHoje() {
        ContaPagar vencida = titulo(1, SituacaoContaPagar.ABERTO, "2026-11-10");
        ContaPagar hoje = titulo(2, SituacaoContaPagar.ABERTO, "2026-11-14");
        ContaPagar futura = titulo(3, SituacaoContaPagar.ABERTO, "2026-12-14");
        ContaPagar paga = titulo(4, SituacaoContaPagar.PAGO, "2026-12-20");
        ContaPagar comBaixa = titulo(5, SituacaoContaPagar.ABERTO, "2026-12-21");
        assertThat(RecorrenciaCalculo.selecionarEncerraveis(List.of(vencida, hoje, futura, paga, comBaixa), d("2026-11-14"), Set.of(5L)))
                .containsExactlyInAnyOrder(hoje, futura);
    }

    // ---------- validação de arquivos ----------

    private static byte[] pdf() {
        return "%PDF-1.4\nconteudo".getBytes();
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0};
    }

    private static byte[] jpg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    }

    @Test
    void aceitaPdfJpgEPngPelaAssinatura() {
        assertThat(AnexoValidacao.validar(pdf(), "boleto.PDF").tipoConteudo()).isEqualTo("application/pdf");
        assertThat(AnexoValidacao.validar(jpg(), "foto.jpg").tipoConteudo()).isEqualTo("image/jpeg");
        assertThat(AnexoValidacao.validar(jpg(), "foto.jpeg").tipoConteudo()).isEqualTo("image/jpeg");
        assertThat(AnexoValidacao.validar(png(), "comprovante.png").tipoConteudo()).isEqualTo("image/png");
    }

    @Test
    void recusaExtensaoFalsaOuDiferenteDoConteudo() {
        assertThatThrownBy(() -> AnexoValidacao.validar("MZ executavel".getBytes(), "virus.pdf")).hasMessageContaining("não permitido");
        assertThatThrownBy(() -> AnexoValidacao.validar(pdf(), "documento.png")).hasMessageContaining("extensão");
        assertThatThrownBy(() -> AnexoValidacao.validar(png(), "foto.jpg")).hasMessageContaining("extensão");
        assertThatThrownBy(() -> AnexoValidacao.validar(pdf(), "semextensao")).hasMessageContaining("extensão");
        assertThatThrownBy(() -> AnexoValidacao.validar("texto puro".getBytes(), "nota.txt")).hasMessageContaining("não permitido");
    }

    @Test
    void recusaVazioEAcimaDeCincoMegabytes() {
        assertThatThrownBy(() -> AnexoValidacao.validar(new byte[0], "a.pdf")).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> AnexoValidacao.validar(null, "a.pdf")).isInstanceOf(ItemInvalidoException.class);
        byte[] grande = new byte[(int) AnexoValidacao.TAMANHO_MAXIMO + 1];
        System.arraycopy(pdf(), 0, grande, 0, 8);
        assertThatThrownBy(() -> AnexoValidacao.validar(grande, "a.pdf")).isInstanceOf(ArquivoMuitoGrandeException.class);
        byte[] limite = new byte[(int) AnexoValidacao.TAMANHO_MAXIMO];
        System.arraycopy(pdf(), 0, limite, 0, 8);
        assertThat(AnexoValidacao.validar(limite, "a.pdf").nome()).isEqualTo("a.pdf");
    }

    @Test
    void higienizaONomeDoArquivo() {
        assertThat(AnexoValidacao.sanitizarNome("C:" + (char) 92 + "pasta" + (char) 92 + "boleto novembro.pdf")).isEqualTo("boleto novembro.pdf");
        assertThat(AnexoValidacao.sanitizarNome("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(AnexoValidacao.sanitizarNome("a<b>c|d?.pdf")).isEqualTo("a_b_c_d_.pdf");
        assertThat(AnexoValidacao.sanitizarNome("...oculto.pdf")).isEqualTo("oculto.pdf");
        assertThat(AnexoValidacao.sanitizarNome(null)).isEqualTo("arquivo");
        assertThat(AnexoValidacao.sanitizarNome("Conta de luz - Novembro.pdf")).isEqualTo("Conta de luz - Novembro.pdf");
        String longo = "x".repeat(300) + ".pdf";
        String nome = AnexoValidacao.sanitizarNome(longo);
        assertThat(nome).hasSize(AnexoValidacao.NOME_MAXIMO).endsWith(".pdf");
    }
}
