package com.manager.app.joia.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JoiaRelacoesDTO {
    private Long categoriaId;
    private List<Long> gruposIds;
}
