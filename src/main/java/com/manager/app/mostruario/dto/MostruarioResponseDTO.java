package com.manager.app.mostruario.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@JsonPropertyOrder({ "id", "descricao", "createdAt", "updatedAt" })
public class MostruarioResponseDTO {

    private Long id;
    private String descricao;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
