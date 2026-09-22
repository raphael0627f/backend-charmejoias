package com.manager.app.transportadora;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransportadoraMapper {
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Transportadora toEntity(TransportadoraDTO dto);

    TransportadoraDTO toDTO(Transportadora entity);
}
