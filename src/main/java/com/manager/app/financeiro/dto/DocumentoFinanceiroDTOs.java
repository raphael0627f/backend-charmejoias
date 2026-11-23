package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.IntervaloParcelamento;
import com.manager.app.financeiro.SituacaoContaReceber;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// resumo financeiro e geração de títulos a receber a partir do pré-acerto e do acerto de consignação
public final class DocumentoFinanceiroDTOs {

    private DocumentoFinanceiroDTOs() {}

    public record Titulo(Long id, Long numero, Integer parcelaNumero, Integer parcelaTotal, BigDecimal valor, LocalDate vencimento,
                         SituacaoContaReceber situacao, BigDecimal valorRecebido, boolean linkAutomaticoAtivo, BigDecimal valorTaxaCliente,
                         boolean pagoPeloLink, BigDecimal valorPago, BigDecimal valorCreditoAplicado) {
    }

    // título da consignação (de qualquer pré-acerto ou do acerto, inclusive cancelado). doDocumento: pertence ao documento aberto (só esses têm ações)
    public record TituloConsignacao(Long id, Long numero, String descricao, Integer parcelaNumero, Integer parcelaTotal, BigDecimal valor, LocalDate vencimento,
                                    SituacaoContaReceber situacao, boolean cancelado, boolean doDocumento, Origem origem, BigDecimal valorRecebido,
                                    BigDecimal valorPago, BigDecimal valorCreditoAplicado, BigDecimal valorTaxaCliente, boolean linkAutomaticoAtivo,
                                    boolean pagoPeloLink) {
    }

    // tipo: PRE_ACERTO, ACERTO ou OUTRO (título da consignação sem documento); numero = id do documento
    public record Origem(String tipo, Long documentoId, Long numero) {
    }

    // soma da consignação inteira, à parte dos totais do documento; títulos cancelados ficam fora dos valores
    public record TotalConsignacao(int quantidade, int quantidadeCancelados, BigDecimal valorTitulado, BigDecimal valorRecebido, BigDecimal valorPago,
                                   BigDecimal valorCreditoAplicado, BigDecimal valorTaxaCliente, BigDecimal emAberto) {
    }

    // tipo: PRE_ACERTO ou ACERTO. valorTituladoPreAcerto só existe no acerto.
    public record Resumo(String tipo, Long documentoId, Long consignacaoId, String clienteCodigo, String clienteNome, BigDecimal valorDocumento, BigDecimal valorTitulado,
                         BigDecimal valorRecebido, BigDecimal saldoATitular, BigDecimal excedente, BigDecimal excedenteRecebido,
                         BigDecimal valorTituladoPreAcerto, boolean tituloAtivo, boolean bloqueadoParaEdicao, boolean podeGerar,
                         String motivoBloqueio, String aviso, List<Titulo> titulos, ExcedenteDTOs.Resumo excedenteAcerto, ExcedenteDTOs.AjusteProposto ajusteProposto,
                         boolean existeLinkAutomaticoAtivo, BigDecimal valorTaxaClienteTotal, boolean pagoPeloLink,
                         BigDecimal valorPago, BigDecimal valorCreditoAplicado, boolean creditoAplicado,
                         BigDecimal valorVendido, BigDecimal valorComissao, BigDecimal valorAPagar,
                         List<TituloConsignacao> titulosConsignacao, TotalConsignacao totalConsignacao) {
    }

    // todos os campos são opcionais; sem corpo valem os padrões (título simples com vencimento hoje)
    public record GerarRequest(LocalDate dataVencimento, Integer quantidadeParcelas, IntervaloParcelamento intervalo, List<LocalDate> datas,
                               String descricao, Long centroCustoId, Long formaPagamentoId, String observacao, RecebimentoDTOs.Request recebimento,
                               LocalDate dataEmissao) {
    }

    public record GerarResposta(java.util.UUID grupoParcelamentoId, List<ContaReceberResumoDTO> titulos,
                                ContaReceberDetalheDTO.Recebimento recebimento, Resumo resumo) {
    }
}
