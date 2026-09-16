package com.manager.app.notafiscal.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.cliente.Cliente;
import com.manager.app.empresa.Empresa;
import com.manager.app.notafiscal.AmbienteNfe;
import com.manager.app.notafiscal.StatusNotaFiscal;
import com.manager.app.transportadora.Transportadora;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "empresa",
        "cliente",
        "serie",
        "numero",
        "naturezaOperacao",
        "consumidorFinal",
        "indicadorPresenca",
        "ambiente",
        "status",
        "chaveAcesso",
        "protocoloAutorizacao",
        "motivoRejeicao",
        "motivoRejeicaoCancelamento",
        "contoraDocumentId",
        "dataSaidaEntrada",
        "transportadora",
        "modalidadeFrete",
        "informacoesComplementares",
        "descontoTotal",
        "itens",
        "pagamentos",
        "correcoes",
        "createdAt",
        "updatedAt",
        "createdBy",
        "updatedBy"
})
public class NotaFiscalResponseDTO {
    private Long id;
    private Empresa empresa;
    private Cliente cliente;
    private Integer serie;
    private Long numero;
    private String naturezaOperacao;
    private boolean consumidorFinal;
    private Integer indicadorPresenca;
    private AmbienteNfe ambiente;
    private StatusNotaFiscal status;
    private String chaveAcesso;
    private String protocoloAutorizacao;
    private String motivoRejeicao;
    private String motivoRejeicaoCancelamento;
    private String contoraDocumentId;
    private LocalDateTime dataSaidaEntrada;
    private Transportadora transportadora;
    private Integer modalidadeFrete;
    private String informacoesComplementares;
    private BigDecimal descontoTotal;
    private List<NotaFiscalItemResponseDTO> itens;
    private List<NotaFiscalPagamentoResponseDTO> pagamentos;
    private List<NotaFiscalCorrecaoResponseDTO> correcoes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UsuarioResumoDTO createdBy;
    private UsuarioResumoDTO updatedBy;
}
