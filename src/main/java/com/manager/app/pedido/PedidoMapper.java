package com.manager.app.pedido;

import com.manager.app.pedido.dto.PedidoResponseDTO;
import org.mapstruct.Mapper;
import com.manager.app.cliente.Cliente;
import org.mapstruct.Mapping;
import org.mapstruct.Named;


@Mapper(componentModel = "spring", uses = {PedidoProdutoMapper.class, com.manager.app.usuario.UsuarioResumoMapper.class})
public interface PedidoMapper {

    @Mapping(source = "cliente.id", target = "clienteId")
    @Mapping(source = "cliente.codigo", target = "clienteCodigo")
    @Mapping(source = "cliente.nome", target = "clienteNome")
    @Mapping(source = "produtos", target = "produtosResponse", qualifiedByName = "pedidoProdutoToDTO")
    @Mapping(source = "cliente", target = "cidade", qualifiedByName = "cidadePrincipal")
    PedidoResponseDTO toResponseDTO(Pedido entity);

    // município do endereço principal da cliente (mesma regra do resumo da listagem)
    @Named("cidadePrincipal")
    default String cidadePrincipal(Cliente cliente) {
        if (cliente == null || cliente.getEnderecoPrincipal() == null) return null;
        return cliente.getEnderecoPrincipal().getMunicipio();
    }
}
