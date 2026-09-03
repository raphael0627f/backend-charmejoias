package com.manager.app.notafiscal.dto;

import com.manager.app.notafiscal.AmbienteNfe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.manager.app.cliente.Cliente;
import com.manager.app.empresa.Empresa;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalRequestDTO {

    @NotNull(message = "Empresa é obrigatória")
    private Long empresaId;

    @NotNull(message = "Cliente é obrigatório")
    private UUID clienteId;

    @NotBlank(message = "Natureza da operação é obrigatória")
    private String naturezaOperacao;

    @NotNull(message = "Indicador de consumidor final é obrigatório")
    private Boolean consumidorFinal;

    @NotNull(message = "Indicador de presença é obrigatório")
    private Integer indicadorPresenca;

    @NotNull(message = "Ambiente é obrigatório")
    private AmbienteNfe ambiente;

    @NotEmpty(message = "A nota precisa de ao menos um item")
    @Valid
    private List<NotaFiscalItemRequestDTO> itens;

    @NotEmpty(message = "A nota precisa de ao menos uma forma de pagamento")
    @Valid
    private List<NotaFiscalPagamentoRequestDTO> pagamentos;
}
