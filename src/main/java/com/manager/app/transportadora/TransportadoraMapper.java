package com.manager.app.transportadora;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransportadoraMapper {
    Transportadora toEntity(TransportadoraDTO dto);

    TransportadoraDTO toDTO(Transportadora entity);
}
