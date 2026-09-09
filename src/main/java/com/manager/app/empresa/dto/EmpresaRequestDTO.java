package com.manager.app.empresa.dto;

import com.manager.app.endereco.EnderecoDTO;
import com.manager.app.empresa.RegimeTributario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EmpresaRequestDTO {

    private Long id;

    @NotBlank(message = "Razão social é obrigatória")
    private String razaoSocial;

    private String nomeFantasia;

    @NotBlank(message = "CNPJ é obrigatório")
    private String cnpj;

    private String inscricaoEstadual;
    private String cnae;
    private String telefone;

    @NotNull(message = "Regime tributário é obrigatório")
    private RegimeTributario regimeTributario;

    @NotNull(message = "Endereço é obrigatório")
    @Valid
    private EnderecoDTO endereco;

    // opcionais: só usados no update, pra reconciliar numeração já usada fora do sistema
    // (testes manuais no console da Contora, migração de outro sistema, etc.)
    private Integer serieNfe;
    private Long proximoNumeroNfe;
}
