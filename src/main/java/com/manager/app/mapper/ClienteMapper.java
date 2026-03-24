package com.manager.app.mapper;

import com.manager.app.dto.ClienteDTO;
import com.manager.app.entity.Cliente;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClienteMapper {
    Cliente toEntity(ClienteDTO dto);

    ClienteDTO toDTO(Cliente entity);
}
