package com.manager.app.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// situação financeira do cliente no Contas a Receber (individual e visão geral)
public final class SituacaoClienteDTOs {

    private SituacaoClienteDTOs() {}

    public record Cliente(UUID id, String codigo, String nome, String documento, String cidade) {
    }

    public record Faixa(BigDecimal valor, int quantidade) {
    }

    public record TituloVencido(Long id, Long numero, LocalDate vencimento, BigDecimal valorAberto) {
    }

    public record Periodo(LocalDate de, LocalDate ate, BigDecimal valor, int quantidade) {
    }

    public record UltimoRecebimento(LocalDate data, BigDecimal valor) {
    }

    public record Titulos(int total, int abertos, int recebidos, int cancelados) {
    }

    public record Situacao(Cliente cliente, Faixa emAberto, Faixa vencido, Faixa vencemEm7Dias, Faixa vencemEm30Dias, int diasMaiorAtraso,
                           TituloVencido tituloMaisAntigoVencido, Periodo recebidoNoPeriodo, UltimoRecebimento ultimoRecebimento, Titulos titulos,
                           BigDecimal creditoDisponivel, APagar aPagar) {
    }

    // contas a pagar do cliente como favorecido (devoluções e lançamentos vinculados)
    public record APagar(int quantidade, BigDecimal emAberto, BigDecimal vencido) {
    }

    public record Linha(Cliente cliente, BigDecimal emAberto, BigDecimal vencido, int quantidadeTitulosAbertos, int diasMaiorAtraso,
                        UltimoRecebimento ultimoRecebimento) {
    }

    public record Totais(BigDecimal totalEmAberto, BigDecimal totalVencido, long clientesComAtraso, long quantidadeClientes) {
    }

    public record Geral(List<Linha> content, int page, int size, long totalElements, int totalPages, Totais totais) {
    }
}
