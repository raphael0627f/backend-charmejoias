package com.manager.app.financeiro;

import com.manager.app.shared.TextoUtils;
import com.manager.app.shared.exception.ItemInvalidoException;
import com.manager.app.usuario.Usuario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// regras e cálculos do financeiro sem acesso a banco de dados (testáveis em JUnit puro).
// Valores em BigDecimal com 2 casas, arredondamento HALF_UP; saldo = soma das movimentações (entrada +, saída -).
public final class CalculoFinanceiro {

    public static final int ESCALA = 2;
    public static final int MOTIVO_MINIMO = 5;
    public static final int MOTIVO_MAXIMO = 255;

    private CalculoFinanceiro() {}

    public record ResultadoTransferencia(TransferenciaBanco transferencia, MovimentacaoBanco saida, MovimentacaoBanco entrada) {}

    public static BigDecimal arredondar(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(ESCALA) : valor.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    // entrada soma, saída subtrai
    public static BigDecimal assinado(TipoMovimentacao tipo, BigDecimal valor) {
        BigDecimal v = arredondar(valor);
        return tipo == TipoMovimentacao.ENTRADA ? v : v.negate();
    }

    // saldo do banco: soma de todas as movimentações (o estorno é uma movimentação inversa, então anula a original)
    public static BigDecimal saldo(Collection<MovimentacaoBanco> movimentacoes) {
        BigDecimal total = BigDecimal.ZERO.setScale(ESCALA);
        for (MovimentacaoBanco m : movimentacoes) total = total.add(assinado(m.getTipo(), m.getValor()));
        return total;
    }

    // saldo após cada movimentação, na ordem recebida (cronológica), partindo do saldo anterior ao primeiro item
    public static List<BigDecimal> saldosApos(List<MovimentacaoBanco> cronologicas, BigDecimal saldoAnterior) {
        List<BigDecimal> saldos = new ArrayList<>();
        BigDecimal corrente = arredondar(saldoAnterior);
        for (MovimentacaoBanco m : cronologicas) {
            corrente = corrente.add(assinado(m.getTipo(), m.getValor()));
            saldos.add(corrente);
        }
        return saldos;
    }

    // saldo inicial do banco vira a primeira movimentação; saldo zero não gera movimentação (retorna null)
    public static MovimentacaoBanco movimentoSaldoInicial(Banco banco, Usuario usuario) {
        BigDecimal saldo = arredondar(banco.getSaldoInicial());
        if (saldo.signum() == 0) return null;
        MovimentacaoBanco m = new MovimentacaoBanco();
        m.setBanco(banco);
        m.setData(banco.getDataSaldoInicial());
        m.setTipo(saldo.signum() > 0 ? TipoMovimentacao.ENTRADA : TipoMovimentacao.SAIDA);
        m.setValor(saldo.abs());
        m.setOrigem(OrigemMovimentacao.SALDO_INICIAL);
        m.setDescricao("Saldo inicial");
        m.setUsuario(usuario);
        return m;
    }

    public static BigDecimal validarValorPositivo(BigDecimal valor) {
        if (valor == null || arredondar(valor).signum() <= 0) {
            throw new ItemInvalidoException("O valor deve ser maior que zero.");
        }
        if (valor.stripTrailingZeros().scale() > ESCALA) {
            throw new ItemInvalidoException("O valor deve ter no máximo duas casas decimais.");
        }
        return arredondar(valor);
    }

    public static void validarDataNaoFutura(LocalDate data, LocalDate hoje) {
        if (data == null) throw new ItemInvalidoException("A data é obrigatória.");
        if (data.isAfter(hoje)) {
            throw new ItemInvalidoException("A data informada não pode ser posterior à data atual (" + TextoUtils.data(hoje) + ").");
        }
    }

    public static String validarMotivo(String motivo) {
        String m = motivo == null ? "" : motivo.trim();
        if (m.length() < MOTIVO_MINIMO) {
            throw new ItemInvalidoException("Informe o motivo com pelo menos " + MOTIVO_MINIMO + " caracteres.");
        }
        TextoUtils.validarTexto(m, "O motivo", MOTIVO_MAXIMO);
        return m;
    }

    public static void validarBancoAtivo(Banco banco) {
        if (!banco.isAtivo()) {
            throw new ItemInvalidoException("O banco \"" + banco.getNome() + "\" está inativo e não pode receber movimentações.");
        }
    }

    // transferência entre bancos diferentes: gera a saída na origem e a entrada no destino, ligadas à transferência
    public static ResultadoTransferencia criarTransferencia(Banco origem, Banco destino, BigDecimal valor, LocalDate data,
                                                            String descricao, Usuario usuario, LocalDate hoje) {
        if (origem == null || destino == null) throw new ItemInvalidoException("Informe os bancos de origem e de destino.");
        if (origem.getId() != null && origem.getId().equals(destino.getId())) {
            throw new ItemInvalidoException("O banco de origem e o de destino devem ser diferentes.");
        }
        BigDecimal v = validarValorPositivo(valor);
        validarDataNaoFutura(data, hoje);
        validarBancoAtivo(origem);
        validarBancoAtivo(destino);
        String texto = descricao == null || descricao.isBlank() ? null : descricao.trim();
        TextoUtils.validarTexto(texto, "A descrição", 200);

        TransferenciaBanco t = new TransferenciaBanco();
        t.setBancoOrigem(origem);
        t.setBancoDestino(destino);
        t.setValor(v);
        t.setData(data);
        t.setDescricao(texto);

        String complemento = texto == null ? "" : " - " + texto;
        MovimentacaoBanco saida = movimentacao(origem, data, TipoMovimentacao.SAIDA, v, OrigemMovimentacao.TRANSFERENCIA,
                "Transferência para " + destino.getNome() + complemento, usuario);
        MovimentacaoBanco entrada = movimentacao(destino, data, TipoMovimentacao.ENTRADA, v, OrigemMovimentacao.TRANSFERENCIA,
                "Transferência de " + origem.getNome() + complemento, usuario);
        saida.setTransferencia(t);
        entrada.setTransferencia(t);
        return new ResultadoTransferencia(t, saida, entrada);
    }

    // ajuste manual de saldo, com motivo obrigatório
    public static MovimentacaoBanco criarAjuste(Banco banco, TipoMovimentacao tipo, BigDecimal valor, LocalDate data,
                                                String motivo, Usuario usuario, LocalDate hoje) {
        if (banco == null) throw new ItemInvalidoException("Informe o banco.");
        if (tipo == null) throw new ItemInvalidoException("Informe se o ajuste é de entrada ou de saída.");
        BigDecimal v = validarValorPositivo(valor);
        validarDataNaoFutura(data, hoje);
        validarBancoAtivo(banco);
        String m = validarMotivo(motivo);
        return movimentacao(banco, data, tipo, v, OrigemMovimentacao.AJUSTE, "Ajuste: " + m, usuario);
    }

    // só transferência e ajuste podem ser estornados (nesta fase), uma única vez
    public static void validarEstornavel(MovimentacaoBanco m) {
        if (m.getOrigem() == OrigemMovimentacao.BAIXA_CONTA_PAGAR || m.getOrigem() == OrigemMovimentacao.ESTORNO_BAIXA) {
            throw new ItemInvalidoException("Esta movimentação foi gerada por uma baixa de conta a pagar. Para estornar, utilize o estorno da baixa no título.");
        }
        if (m.getOrigem() != OrigemMovimentacao.TRANSFERENCIA && m.getOrigem() != OrigemMovimentacao.AJUSTE) {
            throw new ItemInvalidoException("Somente transferências e ajustes podem ser estornados por esta operação.");
        }
        if (m.isEstornada()) {
            throw new ItemInvalidoException("Esta movimentação já foi estornada.");
        }
    }

    // marca a movimentação original como estornada e devolve a movimentação inversa (mesmo valor, tipo oposto, mesmo banco)
    public static MovimentacaoBanco estornar(MovimentacaoBanco original, String motivo, Usuario usuario, LocalDate hoje, LocalDateTime agora) {
        validarEstornavel(original);
        validarBancoAtivo(original.getBanco());
        original.setEstornada(true);
        original.setMotivoEstorno(motivo);
        original.setEstornadaEm(agora);
        original.setEstornadaPor(usuario);
        MovimentacaoBanco inversa = movimentacao(original.getBanco(), hoje, original.getTipo().inverso(), original.getValor(),
                OrigemMovimentacao.ESTORNO_MANUAL, "Estorno: " + motivo, usuario);
        inversa.setEstornoDe(original);
        inversa.setTransferencia(original.getTransferencia());
        return inversa;
    }

    static MovimentacaoBanco movimentacao(Banco banco, LocalDate data, TipoMovimentacao tipo, BigDecimal valor,
                                                  OrigemMovimentacao origem, String descricao, Usuario usuario) {
        MovimentacaoBanco m = new MovimentacaoBanco();
        m.setBanco(banco);
        m.setData(data);
        m.setTipo(tipo);
        m.setValor(arredondar(valor));
        m.setOrigem(origem);
        m.setDescricao(descricao != null && descricao.length() > 255 ? descricao.substring(0, 255) : descricao);
        m.setUsuario(usuario);
        return m;
    }
}
