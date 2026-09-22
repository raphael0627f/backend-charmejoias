package com.manager.app.fornecedor;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FornecedorMapper {
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Fornecedor toEntity(FornecedorDTO dto);

    FornecedorDTO toDTO(Fornecedor entity);
}
