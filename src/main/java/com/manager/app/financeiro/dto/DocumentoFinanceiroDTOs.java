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
                         SituacaoContaReceber situacao, BigDecimal valorRecebido) {
    }

    // tipo: PRE_ACERTO ou ACERTO. valorTituladoPreAcerto só existe no acerto.
    public record Resumo(String tipo, Long documentoId, Long consignacaoId, String clienteNome, BigDecimal valorDocumento, BigDecimal valorTitulado,
                         BigDecimal valorRecebido, BigDecimal saldoATitular, BigDecimal excedente, BigDecimal excedenteRecebido,
                         BigDecimal valorTituladoPreAcerto, boolean tituloAtivo, boolean bloqueadoParaEdicao, boolean podeGerar,
                         String motivoBloqueio, String aviso, List<Titulo> titulos) {
    }

    // todos os campos são opcionais; sem corpo valem os padrões (título simples com vencimento hoje)
    public record GerarRequest(LocalDate dataVencimento, Integer quantidadeParcelas, IntervaloParcelamento intervalo, List<LocalDate> datas,
                               String descricao, Long centroCustoId, Long formaPagamentoId, String observacao, RecebimentoDTOs.Request recebimento) {
    }

    public record GerarResposta(java.util.UUID grupoParcelamentoId, List<ContaReceberResumoDTO> titulos,
                                ContaReceberDetalheDTO.Recebimento recebimento, Resumo resumo) {
    }
}
