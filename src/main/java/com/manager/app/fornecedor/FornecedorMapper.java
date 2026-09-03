package com.manager.app.fornecedor;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FornecedorMapper {
    Fornecedor toEntity(FornecedorDTO dto);

    FornecedorDTO toDTO(Fornecedor entity);
}
