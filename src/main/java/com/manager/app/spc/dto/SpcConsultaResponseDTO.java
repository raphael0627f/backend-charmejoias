package com.manager.app.spc.dto;

import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class SpcConsultaResponseDTO {
    private UUID id;
    private Long protocoloNumero;
    private Integer protocoloDigito;
    private Boolean restricao;
    private Map<String, Object> consumidor;
    private List<String> avisos;
    private List<SpcInsumoResultadoDTO> insumos;
    private LocalDateTime createdAt;
    private UsuarioResumoDTO createdBy;
}
