package com.manager.app.spc.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class SpcConsultaResumoDTO {
    private UUID id;
    private UUID clienteId;
    private String clienteNome;
    private Integer codigoProduto;
    private String nomeProduto;
    private Long protocoloNumero;
    private boolean possuiRestricao;
    private LocalDateTime createdAt;
}
