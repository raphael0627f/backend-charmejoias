package com.manager.app.notafiscal.dto;

import com.manager.app.notafiscal.StatusCorrecao;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalCorrecaoResponseDTO {
    private Long id;
    private Integer sequencia;
    private String texto;
    private StatusCorrecao status;
    private String protocolo;
    private String motivoRejeicao;
    private LocalDateTime createdAt;
}
