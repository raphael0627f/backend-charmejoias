package com.manager.app.dto.notafiscal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.entity.Cliente;
import com.manager.app.entity.Empresa;
import com.manager.app.infra.restEnum.AmbienteNfe;
import com.manager.app.infra.restEnum.StatusNotaFiscal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
        "contoraDocumentId",
        "itens",
        "pagamentos",
        "createdAt",
        "updatedAt"
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
    private String contoraDocumentId;
    private List<NotaFiscalItemResponseDTO> itens;
    private List<NotaFiscalPagamentoResponseDTO> pagamentos;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
