package com.manager.app.montagem;

import com.manager.app.montagem.dto.MontagemResponseDTO;
import com.manager.app.montagem.dto.MontagemResumoDTO;
import com.manager.app.mostruario.MostruarioMapper;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UsuarioResumoMapper.class, MostruarioMapper.class})
public interface MontagemMostruarioMapper {

    @Mapping(target = "clienteId", source = "cliente.id")
    MontagemResponseDTO toResponseDTO(MontagemMostruario entity);

    // listagem: sem as listas de consignações/pedidos/instruções (as quantidades são preenchidas pelo controller)
    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "quantidadeConsignacoes", ignore = true)
    @Mapping(target = "quantidadePedidos", ignore = true)
    @Mapping(target = "instrucoesTotal", ignore = true)
    @Mapping(target = "instrucoesConcluidas", ignore = true)
    @Mapping(target = "pedidosSeparados", ignore = true)
    @Mapping(target = "produtosNecessarios", ignore = true)
    @Mapping(target = "produtosIncluidos", ignore = true)
    @Mapping(target = "andamento", ignore = true)
    MontagemResumoDTO toResumoDTO(MontagemMostruario entity);
}
