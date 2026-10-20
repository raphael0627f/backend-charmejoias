package com.manager.app.montagem.dto;

import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class MontagemInstrucaoResponseDTO {
    private Long id;
    private String texto;
    private boolean concluida;
    private LocalDateTime concluidaEm;
    private UsuarioResumoDTO concluidaPor;
}
