package com.manager.app.email.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.usuario.dto.UsuarioResumoDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// a senha NUNCA e devolvida: so senhaConfigurada (true/false)
@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "host", "porta", "usuario", "senhaConfigurada", "remetente", "nomeRemetente", "starttls", "ssl", "timeoutMs",
        "ativo", "usandoVariaveisAmbiente", "atualizadoEm", "atualizadoPor" })
public class ConfiguracaoEmailResponseDTO {
    private String host;
    private Integer porta;
    private String usuario;
    private boolean senhaConfigurada;
    private String remetente;
    private String nomeRemetente;
    private boolean starttls;
    private boolean ssl;
    private Integer timeoutMs;
    private boolean ativo;
    // true quando nada foi salvo no banco e o envio usa as variaveis de ambiente MAIL_* (fallback)
    private boolean usandoVariaveisAmbiente;
    private LocalDateTime atualizadoEm;
    private UsuarioResumoDTO atualizadoPor;
}
