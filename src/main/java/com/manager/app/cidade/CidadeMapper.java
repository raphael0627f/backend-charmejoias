package com.manager.app.cidade;

import com.manager.app.cidade.dto.CidadeResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CidadeMapper {

    @Mapping(target = "descricao", expression = "java(descricao(entity))")
    CidadeResponseDTO toResponseDTO(Cidade entity);

    default String descricao(Cidade cidade){
        if (cidade.getTipo() == CidadeTipo.DISTRITO) {
            return cidade.getNome() + " (distrito de " + cidade.getMunicipioNome() + ") - " + cidade.getUf();
        }
        return cidade.getNome() + " - " + cidade.getUf();
    }
}
