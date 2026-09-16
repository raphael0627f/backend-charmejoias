package com.manager.app.usuario;

import com.manager.app.usuario.dto.UsuarioRequestDTO;
import com.manager.app.usuario.dto.UsuarioResponseDTO;
import com.manager.app.endereco.EnderecoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { EnderecoMapper.class, UsuarioResumoMapper.class })
public interface UsuarioMapper {

    // a senha nunca é copiada automaticamente pro entity: o service é quem faz o hash
    // (passwordEncoder.encode) e atribui explicitamente - nunca deixar o mapper mover a senha
    // em texto puro pra dentro da entidade
    @Mapping(target = "senha", ignore = true)
    @Mapping(target = "perfil", ignore = true)
    Usuario toEntity(UsuarioRequestDTO dto);

    UsuarioResponseDTO toResponseDTO(Usuario entity);
}
