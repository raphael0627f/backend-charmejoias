package com.manager.app.ncm;

import com.manager.app.ncm.dto.NcmDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NcmMapper {
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Ncm toEntity(NcmDTO dto);

    NcmDTO toDTO(Ncm entity);
}
