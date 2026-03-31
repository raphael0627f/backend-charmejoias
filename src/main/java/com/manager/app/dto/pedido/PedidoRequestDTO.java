package com.manager.app.dto.pedido;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.manager.app.entity.*;
import com.manager.app.infra.restEnum.StatusPedido;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PedidoRequestDTO {

    private Long id;
    private String observacao;

    @NotNull(message = "Data de entrega é obrigatória")
    private LocalDate dataEntrega;

    @NotNull(message = "Status é obrigatório")
    private StatusPedido status;

    @NotNull(message = "clienteId é obrigatório")
    private UUID clienteId;

    @NotNull(message = "Produto é obrigatório")
    private List<PedidoProdutoRequestDTO> produtosRequest = new ArrayList<>();

}
