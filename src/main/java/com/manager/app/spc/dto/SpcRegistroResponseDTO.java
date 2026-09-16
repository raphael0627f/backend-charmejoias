package com.manager.app.spc.dto;

import com.manager.app.spc.TipoDevedorSpc;
import com.manager.app.spc.TipoOperacaoSpc;
import com.manager.app.spc.TipoPessoaSpc;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SpcRegistroResponseDTO {
    private UUID id;
    private UUID clienteId;
    private String clienteNome;
    private TipoOperacaoSpc tipoOperacao;
    private TipoPessoaSpc tipoPessoa;
    private String numeroContrato;
    private LocalDate dataCompra;
    private LocalDate dataVencimento;
    private TipoDevedorSpc codigoTipoDevedor;
    private BigDecimal valorDebito;
    private Integer naturezaInclusaoId;
    private String naturezaInclusaoNome;
    private Integer motivoExclusaoId;
    private String motivoExclusaoDescricao;
    private String mensagemRetorno;
    private LocalDateTime createdAt;
    private UsuarioResumoDTO createdBy;
}
