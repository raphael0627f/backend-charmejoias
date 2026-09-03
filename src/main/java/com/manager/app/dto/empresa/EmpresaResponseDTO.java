package com.manager.app.dto.empresa;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.manager.app.entity.Endereco;
import com.manager.app.infra.restEnum.RegimeTributario;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({
        "id",
        "razaoSocial",
        "nomeFantasia",
        "cnpj",
        "inscricaoEstadual",
        "cnae",
        "regimeTributario",
        "endereco",
        "contoraCompanyId",
        "certificadoEnviado",
        "certificadoEnviadoEm",
        "proximoNumeroNfe",
        "serieNfe",
        "createdAt",
        "updatedAt"
})
public class EmpresaResponseDTO {

    private Long id;
    private String razaoSocial;
    private String nomeFantasia;
    private String cnpj;
    private String inscricaoEstadual;
    private String cnae;
    private RegimeTributario regimeTributario;
    private Endereco endereco;
    private String contoraCompanyId;
    private boolean certificadoEnviado;
    private LocalDateTime certificadoEnviadoEm;
    private Long proximoNumeroNfe;
    private Integer serieNfe;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
