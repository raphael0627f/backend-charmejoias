package com.manager.app.mapper;

import com.manager.app.dto.FornecedorDTO;
import com.manager.app.entity.Fornecedor;

public interface FornecedorMapper {
    Fornecedor toEntity(FornecedorDTO dto);

    FornecedorDTO toDTO(Fornecedor entity);
}
