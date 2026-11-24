package com.manager.app.fluxocaixa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// respostas do fluxo de caixa (contrato em docs/fluxo-caixa-indicadores-contrato-frontend.md)
public final class FluxoCaixaDTOs {

    private FluxoCaixaDTOs() {}

    public record Periodo(LocalDate inicio, LocalDate fim, String visao, LocalDate hoje) {
    }

    public record Atrasado(BigDecimal valor, long quantidade) {
    }

    public record Resumo(BigDecimal saldoInicial, BigDecimal entradasRealizadas, BigDecimal entradasPrevistas, BigDecimal saidasRealizadas,
                         BigDecimal saidasPrevistas, BigDecimal saidasPrevistasEstimadas, BigDecimal outrasMovimentacoes, BigDecimal resultadoRealizado,
                         BigDecimal resultadoPrevisto, BigDecimal saldoFinalRealizado, BigDecimal saldoFinalProjetado, Atrasado atrasadosReceber,
                         Atrasado atrasadosPagar) {
    }

    public record Ponto(String periodo, String rotulo, BigDecimal entradasRealizadas, BigDecimal entradasPrevistas, BigDecimal saidasRealizadas,
                        BigDecimal saidasPrevistas, BigDecimal atrasadosReceber, BigDecimal atrasadosPagar, BigDecimal saldoAcumuladoRealizado,
                        BigDecimal saldoAcumuladoProjetado) {
    }

    public record PorBanco(Long bancoId, String nome, BigDecimal saldoInicial, BigDecimal entradasRealizadas, BigDecimal saidasRealizadas,
                           BigDecimal outrasMovimentacoes, BigDecimal saldoFinalRealizado) {
    }

    public record PorCategoria(Long categoriaId, String nome, String pai, BigDecimal realizado, BigDecimal previsto, BigDecimal atrasado,
                               BigDecimal percentualDoTotal) {
    }

    public record Categorias(List<PorCategoria> receitas, List<PorCategoria> despesas) {
    }

    public record Resposta(Periodo periodo, boolean saldosVisiveis, String saldosIndisponiveisMotivo, boolean previstoSemBanco, Resumo resumo,
                           List<Ponto> serie, List<PorBanco> porBanco, Categorias porCategoria, LocalDate horizonteMaximoConhecido) {
    }

    public record TituloRef(String tipoTitulo, Long id, Long numero) {
    }

    public record Item(String tipo, String situacao, TituloRef titulo, String descricao, String pessoa, String categoria, String centroCusto,
                       String banco, String formaPagamento, BigDecimal valor, LocalDate data, boolean estimado, Long diasEmAtraso) {
    }

    public record Detalhe(int page, int size, long totalElementos, int totalPaginas, BigDecimal totalValor, List<Item> itens) {
    }
}
