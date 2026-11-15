package com.manager.app.financeiro.dto;

import com.manager.app.financeiro.SituacaoContaReceber;
import com.manager.app.financeiro.SituacaoExibicaoContaReceber;
import com.manager.app.usuario.dto.UsuarioResumoDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// recebimentos e anexos seguem vazios até as fases 3 e 4 (o frontend já pode contar com os campos)
public record ContaReceberDetalheDTO(Long id, Long numero, String descricao, ContaReceberResumoDTO.ClienteRef cliente,
                                     ContaReceberResumoDTO.CategoriaRef categoria, ContaReceberResumoDTO.Ref centroCusto,
                                     ContaReceberResumoDTO.Ref formaPrevista, BigDecimal valor, BigDecimal valorRecebido, BigDecimal valorAberto,
                                     LocalDate emissao, String competencia, LocalDate vencimento, String documento, String observacao,
                                     SituacaoContaReceber situacao, SituacaoExibicaoContaReceber situacaoExibicao, UUID grupoParcelamentoId,
                                     Integer parcelaNumero, Integer parcelaTotal, Cancelamento cancelamento, List<Object> recebimentos,
                                     List<ContaReceberResumoDTO> parcelas, List<Evento> eventos, LocalDateTime createdAt, LocalDateTime updatedAt,
                                     UsuarioResumoDTO createdBy, UsuarioResumoDTO updatedBy, List<Object> anexos) {

    public record Cancelamento(String motivo, LocalDateTime em, UsuarioResumoDTO por) {
    }

    public record Evento(Long id, String evento, String detalhe, UsuarioResumoDTO usuario, LocalDateTime dataHora) {
    }
}
