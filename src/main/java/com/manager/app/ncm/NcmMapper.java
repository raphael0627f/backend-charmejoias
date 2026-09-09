package com.manager.app.ncm;

import com.manager.app.ncm.dto.NcmDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NcmMapper {
    Ncm toEntity(NcmDTO dto);

    NcmDTO toDTO(Ncm entity);
}
