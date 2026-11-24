package com.manager.app.indicadorconsignacao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// respostas dos indicadores de consignação (contrato em docs/fluxo-caixa-indicadores-contrato-frontend.md).
// Sem dados: valores nulos e base zero (nunca divisão por zero).
public final class IndicadorDTOs {

    private IndicadorDTOs() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Base(Long acertos, Long titulos) {
        public static Base acertos(long n) {
            return new Base(n, null);
        }

        public static Base titulos(long n) {
            return new Base(null, n);
        }
    }

    public record Periodo(LocalDate inicio, LocalDate fim) {
    }

    public record Vendas(BigDecimal valorVendidoTotal, BigDecimal comissaoTotal, BigDecimal descontoMedioPercentual, BigDecimal comissaoMediaValor,
                         BigDecimal comissaoMediaPercentual, BigDecimal valorVendidoMedioPorAcerto, Base base) {
    }

    public record Faixa(String faixa, BigDecimal valor, long quantidade) {
    }

    public record Inadimplencia(BigDecimal valorTotal, long quantidadeTitulos, List<Faixa> faixas, Base base) {
    }

    public record PrazoRecebimento(BigDecimal diasMedioAposVencimento, BigDecimal atrasoMedioDosPagos, BigDecimal pagosNoPrazoPercentual, Base base) {
    }

    public record Giro(BigDecimal valorVendido, BigDecimal valorDevolvido, BigDecimal percentualVendido, long quantidadeVendida, long quantidadeDevolvida, Base base) {
    }

    public record Resumo(Periodo periodo, Vendas vendas, Inadimplencia inadimplencia, PrazoRecebimento prazoRecebimento, Giro giro) {
    }

    public record ClienteRanking(UUID clienteId, String cliente, String cidade, BigDecimal valorVendido, long acertos, BigDecimal inadimplenciaValor, long inadimplenciaTitulos,
                                 BigDecimal prazoMedioDias, long baseTitulosPagos) {
    }

    public record Ranking(List<ClienteRanking> itens, long totalClientes) {
    }

    public record Mes(String mes, BigDecimal valorVendido, BigDecimal comissao, BigDecimal descontoPercentual, BigDecimal inadimplenciaValor, long inadimplenciaTitulos, long acertos) {
    }

    public record Evolucao(List<Mes> meses, Base base) {
    }

    public record ClienteConcentracao(UUID clienteId, String cliente, BigDecimal valorVendido, BigDecimal percentualDoTotal) {
    }

    public record Concentracao(List<ClienteConcentracao> top5, BigDecimal percentualTop5, BigDecimal valorVendidoTotal, Base base) {
    }

    public record ItemVendido(String tipo, String id, String nome, long quantidade, BigDecimal valorVendido) {
    }

    public record CategoriaVendida(Long categoriaId, String nome, long quantidade, BigDecimal valorVendido) {
    }

    public record MelhoresPiores<T>(List<T> melhores, List<T> piores) {
    }

    public record ProdutosCategorias(MelhoresPiores<ItemVendido> produtos, MelhoresPiores<CategoriaVendida> categorias, Base base) {
    }

    public record Meio(Long formaPagamentoId, String nome, long quantidade, BigDecimal valor, BigDecimal percentualDoValor, BigDecimal taxaClienteAbsorvida) {
    }

    public record MeiosPagamento(List<Meio> meios, BigDecimal taxaClienteTotal, Base base) {
    }
}
