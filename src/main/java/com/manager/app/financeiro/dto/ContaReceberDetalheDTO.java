package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.financeiro.SituacaoExibicaoContaReceber;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ContaReceberDetalheDTO(Long id, Long numero, String descricao, ContaReceberResumoDTO.ClienteRef cliente,
                                     String pagadorNome, String pagadorDocumento, String pagadorExibicao, String tipoPagador,
                                     ContaReceberResumoDTO.Origem origem,
                                     ContaReceberResumoDTO.CategoriaRef categoria, ContaReceberResumoDTO.Ref centroCusto,
                                     ContaReceberResumoDTO.Ref formaPrevista, BigDecimal valor, BigDecimal valorRecebido, BigDecimal valorAberto,
                                     LocalDate emissao, String competencia, LocalDate vencimento, String documento, String observacao,
                                     SituacaoContaReceber situacao, SituacaoExibicaoContaReceber situacaoExibicao, UUID grupoParcelamentoId,
                                     Integer parcelaNumero, Integer parcelaTotal, Cancelamento cancelamento, String motivoCancelamento,
                                     LocalDateTime dataCancelamento, UsuarioResumoDTO canceladoPor, List<Recebimento> recebimentos,
                                     List<ContaReceberResumoDTO> parcelas, List<Evento> eventos, LocalDateTime createdAt, LocalDateTime updatedAt,
                                     UsuarioResumoDTO createdBy, UsuarioResumoDTO updatedBy, List<ContaReceberAnexoDTO> anexos,
                                     @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String aviso,
                                     BigDecimal totalPago, BigDecimal totalPrincipal, BigDecimal totalJuros, BigDecimal totalMulta, BigDecimal totalDesconto,
                                     BigDecimal totalTaxaCliente, BigDecimal valorCreditoAplicado, boolean creditoAplicado, ConsignacaoOrigem consignacao) {

    // aviso formal opcional (hoje só no cancelamento de título com link de pagamento ativo)
    public ContaReceberDetalheDTO comAviso(String novoAviso) {
        return new ContaReceberDetalheDTO(id, numero, descricao, cliente, pagadorNome, pagadorDocumento, pagadorExibicao, tipoPagador, origem, categoria,
                centroCusto, formaPrevista, valor, valorRecebido, valorAberto, emissao, competencia, vencimento, documento, observacao, situacao,
                situacaoExibicao, grupoParcelamentoId, parcelaNumero, parcelaTotal, cancelamento, motivoCancelamento, dataCancelamento, canceladoPor,
                recebimentos, parcelas, eventos, createdAt, updatedAt, createdBy, updatedBy, anexos, novoAviso,
                totalPago, totalPrincipal, totalJuros, totalMulta, totalDesconto, totalTaxaCliente, valorCreditoAplicado, creditoAplicado, consignacao);
    }

    // documento de origem na consignação (nulo quando o título não vem de consignação): valores do pré-acerto/acerto e o valor deste título (parcela)
    public record ConsignacaoOrigem(String tipoDocumento, Long documentoId, Long consignacaoId, BigDecimal valorVendido, BigDecimal valorComissao,
                                    BigDecimal valorAPagar, BigDecimal valorTitulo, Integer parcelaNumero, Integer parcelaTotal) {
    }

    public record Cancelamento(String motivo, LocalDateTime em, UsuarioResumoDTO por) {
    }

    // situacao: ATIVO ou ESTORNADO
    public record Recebimento(Long id, LocalDate dataRecebimento, BigDecimal valorPrincipal, BigDecimal juros, BigDecimal multa, BigDecimal desconto,
                              BigDecimal valorTotal, ContaReceberResumoDTO.Ref banco, ContaReceberResumoDTO.Ref formaPagamento, String observacao,
                              String situacao, boolean estornado, String estornoMotivo, LocalDateTime estornadoEm, UsuarioResumoDTO estornadoPor,
                              LocalDateTime createdAt, UsuarioResumoDTO createdBy, Long movimentacaoId, String origem,
                              BigDecimal valorTaxaCliente, boolean automatico) {
    }

    public record Evento(Long id, String evento, String detalhe, UsuarioResumoDTO usuario, LocalDateTime dataHora) {
    }
}
