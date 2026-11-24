package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.SituacaoContaReceber;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// aba Financeiro do cliente: consignações com valores e selo, títulos por consignação e contas a pagar do cliente
public final class ClienteFinanceiroDTOs {

    private ClienteFinanceiroDTOs() {}

    // documentoTipo: ACERTO, PRE_ACERTO ou nulo (consignação sem documento). valorPago = total real (regra do valor realmente pago); taxa do cliente à parte
    public record Consignacao(Long consignacaoId, Long numero, LocalDate data, String documentoTipo, Long documentoId, BigDecimal valorVendido,
                              BigDecimal valorComissao, BigDecimal valorAPagar, BigDecimal valorPago, BigDecimal valorCreditoAplicado, boolean creditoAplicado,
                              BigDecimal valorTaxaCliente, BigDecimal emAberto, LocalDate vencimentoMaisAntigo, String selo, int quantidadeTitulos,
                              int quantidadeCancelados, BigDecimal valorTitulado) {
    }

    // soma do conjunto filtrado
    public record Resumo(int quantidade, BigDecimal valorVendido, BigDecimal valorComissao, BigDecimal valorAPagar, BigDecimal valorPago,
                         BigDecimal valorCreditoAplicado, BigDecimal emAberto) {
    }

    // títulos do cliente fora de consignação
    public record SemConsignacao(int quantidadeTitulos, int quantidadeCancelados, BigDecimal valorPago, BigDecimal valorCreditoAplicado, BigDecimal emAberto,
                                 LocalDate vencimentoMaisAntigo, String selo) {
    }

    public record ConsignacoesPagina(List<Consignacao> content, int page, int size, long totalElements, int totalPages, String filtro, Resumo resumo,
                                     SemConsignacao semConsignacao) {
    }

    public record TituloLinha(Long id, Long numero, String descricao, Integer parcelaNumero, Integer parcelaTotal, LocalDate vencimento, BigDecimal valor,
                              BigDecimal valorPago, BigDecimal valorCreditoAplicado, BigDecimal emAberto, SituacaoContaReceber situacao, boolean cancelado,
                              DocumentoFinanceiroDTOs.Origem origem) {
    }

    public record TitulosPagina(List<TituloLinha> content, int page, int size, long totalElements, int totalPages) {
    }

    public record PagarLinha(Long id, Long numero, String descricao, LocalDate emissao, LocalDate vencimento, BigDecimal valor, BigDecimal valorPago,
                             BigDecimal emAberto, String situacao, boolean cancelado) {
    }

    public record PagarTotais(long quantidade, BigDecimal valor, BigDecimal valorPago, BigDecimal emAberto) {
    }

    public record PagarPagina(List<PagarLinha> content, int page, int size, long totalElements, int totalPages, PagarTotais totais) {
    }
}
