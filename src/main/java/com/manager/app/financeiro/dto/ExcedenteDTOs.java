package com.manager.app.financeiro.dto;

import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ExcedenteDTOs {

    private ExcedenteDTOs() {}

    // bloco "excedente" do GET /acertos-consignacao/{id}/financeiro (nulo quando o acerto não gerou excedente)
    public record Resumo(Long id, BigDecimal valor, String situacao, String destino, LocalDateTime criadoEm) {
    }

    // proposta de ajuste do título do pré-acerto (opção B), no GET do acerto ainda não confirmado: exige aceite explícito na confirmação
    public record AjusteProposto(BigDecimal tituloPreAcerto, BigDecimal valorAcerto, BigDecimal recebido, BigDecimal valorAjustado, BigDecimal reducao,
                                 boolean exigeAceite, String mensagem) {
    }

    public record Item(Long id, BigDecimal valor, String situacao, String destino, Long acertoId, Long consignacaoId,
                       ContaReceberResumoDTO.ClienteRef cliente, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
    }

    public record Evento(Long id, String evento, String detalhe, UsuarioResumoDTO usuario, LocalDateTime dataHora) {
    }

    // título a pagar da devolução ao cliente (quando o destino é DEVOLUCAO)
    public record Devolucao(Long contaPagarId, Long numero, String situacao, BigDecimal valorPago, java.time.LocalDate vencimento) {
    }

    public record Detalhe(Item excedente, List<Evento> eventos, Devolucao devolucao) {
    }

    public record Lista(List<Item> content, int page, int size, long totalElements, int totalPages) {
    }
}
