package com.manager.app.empresa;

import com.manager.app.empresa.dto.EmpresaRequestDTO;
import com.manager.app.empresa.dto.EmpresaResponseDTO;
import org.mapstruct.Mapper;
import com.manager.app.endereco.EnderecoMapper;

@Mapper(componentModel = "spring", uses = EnderecoMapper.class)
public interface EmpresaMapper {

    Empresa toEntity(EmpresaRequestDTO dto);

    EmpresaResponseDTO toResponseDTO(Empresa entity);
}
