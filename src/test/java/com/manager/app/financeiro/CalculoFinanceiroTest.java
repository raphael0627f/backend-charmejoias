package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// somente cálculos e regras do financeiro (sem banco de dados): saldo, saldo após, transferência, ajuste, estorno e arredondamento
class CalculoFinanceiroTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 14);
    private static final LocalDateTime AGORA = HOJE.atTime(10, 0);

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    private static Banco banco(Long id, String nome, boolean ativo) {
        Banco b = new Banco();
        b.setId(id);
        b.setNome(nome);
        b.setTipo(TipoBanco.CONTA_CORRENTE);
        b.setAtivo(ativo);
        b.setSaldoInicial(BigDecimal.ZERO);
        b.setDataSaldoInicial(HOJE);
        return b;
    }

    private static MovimentacaoBanco mov(Banco banco, TipoMovimentacao tipo, String valor, OrigemMovimentacao origem) {
        MovimentacaoBanco m = new MovimentacaoBanco();
        m.setBanco(banco);
        m.setTipo(tipo);
        m.setValor(v(valor));
        m.setOrigem(origem);
        m.setData(HOJE);
        return m;
    }

    // ---------- arredondamento ----------

    @Test
    void arredondaMeioParaCimaComDuasCasas() {
        assertThat(CalculoFinanceiro.arredondar(v("10.005"))).isEqualByComparingTo("10.01");
        assertThat(CalculoFinanceiro.arredondar(v("10.004"))).isEqualByComparingTo("10.00");
        assertThat(CalculoFinanceiro.arredondar(v("10.1")).scale()).isEqualTo(2);
        assertThat(CalculoFinanceiro.arredondar(null)).isEqualByComparingTo("0.00");
    }

    @Test
    void somaDeValoresDecimaisNaoTemErroDeCentavo() {
        Banco b = banco(1L, "Inter", true);
        List<MovimentacaoBanco> lista = List.of(
                mov(b, TipoMovimentacao.ENTRADA, "0.10", OrigemMovimentacao.AJUSTE),
                mov(b, TipoMovimentacao.ENTRADA, "0.20", OrigemMovimentacao.AJUSTE),
                mov(b, TipoMovimentacao.SAIDA, "0.30", OrigemMovimentacao.AJUSTE));
        assertThat(CalculoFinanceiro.saldo(lista)).isEqualByComparingTo("0.00");
    }

    @Test
    void valorComMaisDeDuasCasasEhRecusado() {
        assertThatThrownBy(() -> CalculoFinanceiro.validarValorPositivo(v("10.005"))).isInstanceOf(ItemInvalidoException.class)
                .hasMessageContaining("duas casas decimais");
        assertThat(CalculoFinanceiro.validarValorPositivo(v("10.50"))).isEqualByComparingTo("10.50");
        assertThat(CalculoFinanceiro.validarValorPositivo(v("10.500"))).isEqualByComparingTo("10.50");
    }

    @Test
    void valorZeroOuNegativoEhRecusado() {
        assertThatThrownBy(() -> CalculoFinanceiro.validarValorPositivo(BigDecimal.ZERO)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> CalculoFinanceiro.validarValorPositivo(v("-1.00"))).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> CalculoFinanceiro.validarValorPositivo(null)).isInstanceOf(ItemInvalidoException.class);
    }

    // ---------- saldo ----------

    @Test
    void saldoEhSaldoInicialMaisEntradasMenosSaidas() {
        Banco b = banco(1L, "Inter", true);
        b.setSaldoInicial(v("1500.00"));
        List<MovimentacaoBanco> lista = new ArrayList<>();
        lista.add(CalculoFinanceiro.movimentoSaldoInicial(b, null));
        lista.add(mov(b, TipoMovimentacao.SAIDA, "300.25", OrigemMovimentacao.TRANSFERENCIA));
        lista.add(mov(b, TipoMovimentacao.ENTRADA, "50.10", OrigemMovimentacao.AJUSTE));
        assertThat(CalculoFinanceiro.saldo(lista)).isEqualByComparingTo("1249.85");
    }

    @Test
    void saldoInicialPositivoViraEntradaNegativoViraSaidaEZeroNaoGeraMovimentacao() {
        Banco b = banco(1L, "Inter", true);
        b.setSaldoInicial(v("100.00"));
        MovimentacaoBanco positivo = CalculoFinanceiro.movimentoSaldoInicial(b, null);
        assertThat(positivo.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(positivo.getOrigem()).isEqualTo(OrigemMovimentacao.SALDO_INICIAL);
        assertThat(positivo.getValor()).isEqualByComparingTo("100.00");

        b.setSaldoInicial(v("-40.50"));
        MovimentacaoBanco negativo = CalculoFinanceiro.movimentoSaldoInicial(b, null);
        assertThat(negativo.getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(negativo.getValor()).isEqualByComparingTo("40.50");
        assertThat(CalculoFinanceiro.saldo(List.of(negativo))).isEqualByComparingTo("-40.50");

        b.setSaldoInicial(BigDecimal.ZERO);
        assertThat(CalculoFinanceiro.movimentoSaldoInicial(b, null)).isNull();
    }

    @Test
    void saldoAposAcumulaNaOrdemCronologicaPartindoDoSaldoAnterior() {
        Banco b = banco(1L, "Inter", true);
        List<MovimentacaoBanco> cronologicas = List.of(
                mov(b, TipoMovimentacao.ENTRADA, "100.00", OrigemMovimentacao.SALDO_INICIAL),
                mov(b, TipoMovimentacao.SAIDA, "30.00", OrigemMovimentacao.TRANSFERENCIA),
                mov(b, TipoMovimentacao.SAIDA, "100.00", OrigemMovimentacao.AJUSTE),
                mov(b, TipoMovimentacao.ENTRADA, "0.05", OrigemMovimentacao.AJUSTE));
        List<BigDecimal> saldos = CalculoFinanceiro.saldosApos(cronologicas, BigDecimal.ZERO);
        assertThat(saldos).hasSize(4);
        assertThat(saldos.get(0)).isEqualByComparingTo("100.00");
        assertThat(saldos.get(1)).isEqualByComparingTo("70.00");
        assertThat(saldos.get(2)).isEqualByComparingTo("-30.00");
        assertThat(saldos.get(3)).isEqualByComparingTo("-29.95");

        // recorte do período: o saldo anterior ao primeiro item entra como ponto de partida
        List<BigDecimal> recorte = CalculoFinanceiro.saldosApos(cronologicas.subList(2, 4), v("70.00"));
        assertThat(recorte.get(0)).isEqualByComparingTo("-30.00");
        assertThat(recorte.get(1)).isEqualByComparingTo("-29.95");
    }

    // ---------- transferência ----------

    @Test
    void transferenciaGeraSaidaNaOrigemEEntradaNoDestinoLigadasEComSaldoTotalInalterado() {
        Banco origem = banco(1L, "Inter", true);
        Banco destino = banco(2L, "Caixa", true);
        CalculoFinanceiro.ResultadoTransferencia r = CalculoFinanceiro.criarTransferencia(origem, destino, v("300.00"),
                HOJE, "Reforço de caixa", null, HOJE);

        assertThat(r.saida().getBanco()).isSameAs(origem);
        assertThat(r.saida().getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(r.entrada().getBanco()).isSameAs(destino);
        assertThat(r.entrada().getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(r.saida().getValor()).isEqualByComparingTo("300.00");
        assertThat(r.entrada().getValor()).isEqualByComparingTo("300.00");
        assertThat(r.saida().getTransferencia()).isSameAs(r.transferencia());
        assertThat(r.entrada().getTransferencia()).isSameAs(r.transferencia());
        assertThat(r.saida().getOrigem()).isEqualTo(OrigemMovimentacao.TRANSFERENCIA);
        assertThat(CalculoFinanceiro.saldo(List.of(r.saida(), r.entrada()))).isEqualByComparingTo("0.00");
        assertThat(r.saida().getDescricao()).contains("Caixa").contains("Reforço de caixa");
    }

    @Test
    void transferenciaRecusaBancosIguaisValorInvalidoDataFuturaEBancoInativo() {
        Banco a = banco(1L, "Inter", true);
        Banco b = banco(2L, "Caixa", true);
        Banco inativo = banco(3L, "Antigo", false);
        assertThatThrownBy(() -> CalculoFinanceiro.criarTransferencia(a, a, v("10.00"), HOJE, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("diferentes");
        assertThatThrownBy(() -> CalculoFinanceiro.criarTransferencia(a, b, BigDecimal.ZERO, HOJE, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> CalculoFinanceiro.criarTransferencia(a, b, v("10.00"), HOJE.plusDays(1), null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("posterior");
        assertThatThrownBy(() -> CalculoFinanceiro.criarTransferencia(a, inativo, v("10.00"), HOJE, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("inativo");
        assertThatThrownBy(() -> CalculoFinanceiro.criarTransferencia(inativo, b, v("10.00"), HOJE, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("inativo");
    }

    @Test
    void transferenciaPermiteDataDeHojeEDeOntem() {
        Banco a = banco(1L, "Inter", true);
        Banco b = banco(2L, "Caixa", true);
        assertThat(CalculoFinanceiro.criarTransferencia(a, b, v("1.00"), HOJE, null, null, HOJE).saida().getData()).isEqualTo(HOJE);
        assertThat(CalculoFinanceiro.criarTransferencia(a, b, v("1.00"), HOJE.minusDays(1), null, null, HOJE).entrada().getData())
                .isEqualTo(HOJE.minusDays(1));
    }

    // ---------- ajuste ----------

    @Test
    void ajusteDeEntradaESaidaAlteraOSaldoEExigeMotivo() {
        Banco b = banco(1L, "Inter", true);
        MovimentacaoBanco entrada = CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.ENTRADA, v("25.50"), HOJE,
                "  Diferença apurada na conciliação ", null, HOJE);
        MovimentacaoBanco saida = CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.SAIDA, v("10.00"), HOJE, "Tarifa não lançada", null, HOJE);
        assertThat(entrada.getOrigem()).isEqualTo(OrigemMovimentacao.AJUSTE);
        assertThat(entrada.getDescricao()).isEqualTo("Ajuste: Diferença apurada na conciliação");
        assertThat(CalculoFinanceiro.saldo(List.of(entrada, saida))).isEqualByComparingTo("15.50");

        assertThatThrownBy(() -> CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.ENTRADA, v("1.00"), HOJE, "curt", null, HOJE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("5 caracteres");
        assertThatThrownBy(() -> CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.ENTRADA, v("1.00"), HOJE, "     ", null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.ENTRADA, v("1.00"), HOJE, null, null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void ajusteRecusaValorZeroDataFuturaEBancoInativo() {
        Banco ativo = banco(1L, "Inter", true);
        Banco inativo = banco(2L, "Antigo", false);
        assertThatThrownBy(() -> CalculoFinanceiro.criarAjuste(ativo, TipoMovimentacao.SAIDA, BigDecimal.ZERO, HOJE, "motivo válido", null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> CalculoFinanceiro.criarAjuste(ativo, TipoMovimentacao.SAIDA, v("1.00"), HOJE.plusDays(1), "motivo válido", null, HOJE))
                .isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> CalculoFinanceiro.criarAjuste(inativo, TipoMovimentacao.SAIDA, v("1.00"), HOJE, "motivo válido", null, HOJE))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("inativo");
    }

    // ---------- estorno ----------

    @Test
    void estornoDeAjusteGeraMovimentacaoInversaEZeraOEfeitoNoSaldo() {
        Banco b = banco(1L, "Inter", true);
        MovimentacaoBanco ajuste = CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.SAIDA, v("40.00"), HOJE, "Lançado por engano", null, HOJE);
        MovimentacaoBanco inversa = CalculoFinanceiro.estornar(ajuste, "Lançamento em duplicidade", null, HOJE, AGORA);

        assertThat(ajuste.isEstornada()).isTrue();
        assertThat(ajuste.getMotivoEstorno()).isEqualTo("Lançamento em duplicidade");
        assertThat(ajuste.getEstornadaEm()).isEqualTo(AGORA);
        assertThat(inversa.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(inversa.getValor()).isEqualByComparingTo("40.00");
        assertThat(inversa.getOrigem()).isEqualTo(OrigemMovimentacao.ESTORNO_MANUAL);
        assertThat(inversa.getEstornoDe()).isSameAs(ajuste);
        assertThat(inversa.getBanco()).isSameAs(b);
        assertThat(CalculoFinanceiro.saldo(List.of(ajuste, inversa))).isEqualByComparingTo("0.00");
    }

    @Test
    void naoEstornaDuasVezesNemOrigensNaoPermitidas() {
        Banco b = banco(1L, "Inter", true);
        MovimentacaoBanco ajuste = CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.ENTRADA, v("5.00"), HOJE, "Ajuste inicial", null, HOJE);
        MovimentacaoBanco inversa = CalculoFinanceiro.estornar(ajuste, "Motivo do estorno", null, HOJE, AGORA);

        assertThatThrownBy(() -> CalculoFinanceiro.estornar(ajuste, "Motivo do estorno", null, HOJE, AGORA))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("já foi estornada");
        assertThatThrownBy(() -> CalculoFinanceiro.validarEstornavel(inversa)).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> CalculoFinanceiro.validarEstornavel(mov(b, TipoMovimentacao.ENTRADA, "10.00", OrigemMovimentacao.SALDO_INICIAL)))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("transferências e ajustes");
        assertThatThrownBy(() -> CalculoFinanceiro.validarEstornavel(mov(b, TipoMovimentacao.SAIDA, "10.00", OrigemMovimentacao.PAGAMENTO_CONTA_PAGAR)))
                .isInstanceOf(ItemInvalidoException.class);
    }

    @Test
    void estornoDeTransferenciaInverteAsDuasPontasERestauraOsSaldos() {
        Banco origem = banco(1L, "Inter", true);
        Banco destino = banco(2L, "Caixa", true);
        CalculoFinanceiro.ResultadoTransferencia r = CalculoFinanceiro.criarTransferencia(origem, destino, v("300.00"), HOJE, null, null, HOJE);

        MovimentacaoBanco inversaSaida = CalculoFinanceiro.estornar(r.saida(), "Valor incorreto", null, HOJE, AGORA);
        MovimentacaoBanco inversaEntrada = CalculoFinanceiro.estornar(r.entrada(), "Valor incorreto", null, HOJE, AGORA);

        assertThat(inversaSaida.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(inversaSaida.getBanco()).isSameAs(origem);
        assertThat(inversaEntrada.getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(inversaEntrada.getBanco()).isSameAs(destino);
        assertThat(CalculoFinanceiro.saldo(List.of(r.saida(), inversaSaida))).isEqualByComparingTo("0.00");
        assertThat(CalculoFinanceiro.saldo(List.of(r.entrada(), inversaEntrada))).isEqualByComparingTo("0.00");
        assertThat(r.saida().isEstornada()).isTrue();
        assertThat(r.entrada().isEstornada()).isTrue();
    }

    @Test
    void estornoEmBancoInativoEhRecusadoSemAlterarAOriginal() {
        Banco b = banco(1L, "Inter", true);
        MovimentacaoBanco ajuste = CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.ENTRADA, v("5.00"), HOJE, "Ajuste inicial", null, HOJE);
        b.setAtivo(false);
        assertThatThrownBy(() -> CalculoFinanceiro.estornar(ajuste, "Motivo do estorno", null, HOJE, AGORA))
                .isInstanceOf(ItemInvalidoException.class).hasMessageContaining("inativo");
        assertThat(ajuste.isEstornada()).isFalse();
    }

    @Test
    void motivoDoEstornoPrecisaTerCincoCaracteresEFicaSemEspacosNasPontas() {
        assertThat(CalculoFinanceiro.validarMotivo("  Erro de digitação  ")).isEqualTo("Erro de digitação");
        assertThatThrownBy(() -> CalculoFinanceiro.validarMotivo("abcd")).isInstanceOf(ItemInvalidoException.class);
        assertThatThrownBy(() -> CalculoFinanceiro.validarMotivo("x".repeat(256))).isInstanceOf(ItemInvalidoException.class);
        assertThat(CalculoFinanceiro.validarMotivo("abcde")).isEqualTo("abcde");
    }

    @Test
    void saldoNegativoEhCalculadoSemBloqueio() {
        Banco b = banco(1L, "Inter", true);
        MovimentacaoBanco saida = CalculoFinanceiro.criarAjuste(b, TipoMovimentacao.SAIDA, v("80.00"), HOJE, "Saída sem saldo", null, HOJE);
        assertThat(CalculoFinanceiro.saldo(List.of(saida))).isEqualByComparingTo("-80.00");
        assertThat(MovimentacaoBancoService.moeda(v("-1234.5"))).isEqualTo("R$ -1.234,50");
    }
}
