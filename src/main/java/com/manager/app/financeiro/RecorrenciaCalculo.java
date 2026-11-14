package com.manager.app.financeiro;

import com.manager.app.shared.exception.ItemInvalidoException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// regras puras da recorrência (sem acesso a banco): datas das ocorrências, janela de geração e seleção das ocorrências afetadas
public final class RecorrenciaCalculo {

    public static final int MESES_JANELA = 12;
    public static final int MAXIMO_OCORRENCIAS = 520;
    private static final int GUARDA = 5000;

    private RecorrenciaCalculo() {}

    // geração vai até 12 meses à frente de hoje ou até a data fim, o que vier antes
    public static LocalDate limiteJanela(LocalDate hoje, LocalDate dataFim) {
        LocalDate limite = hoje.plusMonths(MESES_JANELA);
        return dataFim != null && dataFim.isBefore(limite) ? dataFim : limite;
    }

    // i-ésima ocorrência a partir da data base (sempre calculada da base, para não derivar: 31/01, 28/02, 31/03...; 29/02 -> 28/02)
    public static LocalDate ocorrencia(PeriodicidadeRecorrencia periodicidade, LocalDate inicio, int indice) {
        return switch (periodicidade) {
            case SEMANAL -> inicio.plusWeeks(indice);
            case MENSAL -> inicio.plusMonths(indice);
            case ANUAL -> inicio.plusYears(indice);
        };
    }

    // todas as datas da série de inicio até limite (inclusive)
    public static List<LocalDate> datas(PeriodicidadeRecorrencia periodicidade, LocalDate inicio, LocalDate limite) {
        if (periodicidade == null) throw new ItemInvalidoException("A periodicidade da recorrência é obrigatória.");
        List<LocalDate> datas = new ArrayList<>();
        for (int i = 0; i < GUARDA; i++) {
            LocalDate data = ocorrencia(periodicidade, inicio, i);
            if (data.isAfter(limite)) break;
            datas.add(data);
        }
        return datas;
    }

    // datas da série que ainda não foram geradas (comparação pela data original da ocorrência): base da idempotência da rotina
    public static List<LocalDate> faltantes(PeriodicidadeRecorrencia periodicidade, LocalDate inicio, LocalDate limite, Collection<LocalDate> existentes) {
        Set<LocalDate> geradas = new HashSet<>(existentes);
        return datas(periodicidade, inicio, limite).stream().filter(d -> !geradas.contains(d)).toList();
    }

    // validação do pedido de criação; devolve as datas a gerar
    public static List<LocalDate> validarCriacao(PeriodicidadeRecorrencia periodicidade, LocalDate primeiroVencimento, LocalDate dataFim, LocalDate hoje) {
        if (periodicidade == null) throw new ItemInvalidoException("A periodicidade da recorrência é obrigatória.");
        if (primeiroVencimento == null) throw new ItemInvalidoException("O vencimento é obrigatório e corresponde à primeira ocorrência.");
        if (dataFim != null && dataFim.isBefore(primeiroVencimento)) {
            throw new ItemInvalidoException("A data de término não pode ser anterior ao primeiro vencimento.");
        }
        List<LocalDate> datas = datas(periodicidade, primeiroVencimento, limiteJanela(hoje, dataFim));
        if (datas.isEmpty()) throw new ItemInvalidoException("A recorrência não geraria nenhuma ocorrência no período informado.");
        if (datas.size() > MAXIMO_OCORRENCIAS) {
            throw new ItemInvalidoException("A recorrência geraria mais de " + MAXIMO_OCORRENCIAS + " ocorrências. Informe uma data de início mais recente ou uma data de término.");
        }
        return datas;
    }

    // ocorrência alterável pelo reajuste: aberta, sem baixa e com vencimento a partir da data informada
    public static boolean reajustavel(ContaPagar titulo, LocalDate aPartirDe, boolean comBaixaAtiva) {
        return titulo.getSituacao() == SituacaoContaPagar.ABERTO && !comBaixaAtiva && !titulo.getVencimento().isBefore(aPartirDe);
    }

    // ocorrência cancelável no encerramento: futura (vencimento de hoje em diante), aberta e sem baixa
    public static boolean encerravel(ContaPagar titulo, LocalDate hoje, boolean comBaixaAtiva) {
        return reajustavel(titulo, hoje, comBaixaAtiva);
    }

    public static List<ContaPagar> selecionarReajustaveis(Collection<ContaPagar> titulos, LocalDate aPartirDe, Set<Long> comBaixaAtiva) {
        return titulos.stream().filter(t -> reajustavel(t, aPartirDe, comBaixaAtiva.contains(t.getId()))).toList();
    }

    public static List<ContaPagar> selecionarEncerraveis(Collection<ContaPagar> titulos, LocalDate hoje, Set<Long> comBaixaAtiva) {
        return titulos.stream().filter(t -> encerravel(t, hoje, comBaixaAtiva.contains(t.getId()))).toList();
    }
}
