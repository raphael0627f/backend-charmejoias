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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalRequestDTO {

    @NotNull(message = "O campo Empresa é obrigatório.")
    private Long empresaId;

    @NotNull(message = "O campo Cliente é obrigatório.")
    private UUID clienteId;

    @NotBlank(message = "O campo Natureza da operação é obrigatório.")
    private String naturezaOperacao;

    @NotNull(message = "O campo Indicador de consumidor final é obrigatório.")
    private Boolean consumidorFinal;

    @NotNull(message = "O campo Indicador de presença é obrigatório.")
    private Integer indicadorPresenca;

    @NotNull(message = "O campo Ambiente é obrigatório.")
    private AmbienteNfe ambiente;

    @NotEmpty(message = "Informe ao menos um item na nota.")
    @Valid
    private List<NotaFiscalItemRequestDTO> itens;

    @NotEmpty(message = "Informe ao menos uma forma de pagamento.")
    @Valid
    private List<NotaFiscalPagamentoRequestDTO> pagamentos;

    // desconto único aplicado sobre a nota inteira; quando informado, é rateado entre os itens
    // proporcionalmente ao valor de cada um (mesmo critério que a Contora usa pra frete/seguro),
    // e substitui qualquer desconto informado individualmente em items[].desconto
    private BigDecimal descontoTotal;

    private LocalDateTime dataSaidaEntrada;

    private Long transportadoraId;
    private Integer modalidadeFrete;

    private String informacoesComplementares;
}
