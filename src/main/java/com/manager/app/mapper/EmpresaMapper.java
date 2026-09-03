package com.manager.app.mapper;

import com.manager.app.dto.empresa.EmpresaRequestDTO;
import com.manager.app.dto.empresa.EmpresaResponseDTO;
import com.manager.app.entity.Empresa;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = EnderecoMapper.class)
public interface EmpresaMapper {

    Empresa toEntity(EmpresaRequestDTO dto);

    EmpresaResponseDTO toResponseDTO(Empresa entity);
}
