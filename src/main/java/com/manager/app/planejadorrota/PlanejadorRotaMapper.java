package com.manager.app.planejadorrota;

import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.endereco.EnderecoMapper;
import com.manager.app.planejadorrota.dto.PlanejadorRotaClienteResponseDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaConsignacaoResumoDTO;
import com.manager.app.pedido.Pedido;
import com.manager.app.planejadorrota.dto.PlanejadorRotaPedidoResumoDTO;
import com.manager.app.planejadorrota.dto.PlanejadorRotaResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UsuarioResumoMapper.class, EnderecoMapper.class})
public interface PlanejadorRotaMapper {

    PlanejadorRotaResponseDTO toResponseDTO(PlanejadorRota entity);

    @Mapping(target = "cliente", expression = "java(clienteResumo(entity))")
    PlanejadorRotaClienteResponseDTO toResponseDTO(PlanejadorRotaCliente entity);

    default ClienteResumoDTO clienteResumo(PlanejadorRotaCliente entity){
        if (entity.getCliente() == null) return null;
        return new ClienteResumoDTO(entity.getCliente().getCodigo(), entity.getCliente().getNome());
    }

    // totais (itens, quantidade, valor) entram depois, em lote, em PlanejadorRotaService.toResponseDTO
    default PlanejadorRotaPedidoResumoDTO toPedidoResumoDTO(PlanejadorRotaPedidoDeixar entity){
        if (entity == null) return null;
        Pedido pedido = entity.getPedido();
        PlanejadorRotaPedidoResumoDTO dto = new PlanejadorRotaPedidoResumoDTO();
        dto.setId(pedido.getId());
        dto.setStatus(pedido.getStatus());
        dto.setObservacao(pedido.getObservacao());
        dto.setDataEntrega(pedido.getDataEntrega());
        dto.setConfirmada(entity.getConfirmada());
        return dto;
    }

    default PlanejadorRotaConsignacaoResumoDTO toConsignacaoResumoDTO(PlanejadorRotaConsignacaoDeixar entity){
        if (entity == null) return null;
        Consignacao consignacao = entity.getConsignacao();
        return new PlanejadorRotaConsignacaoResumoDTO(consignacao.getId(), consignacao.getDescricao(), consignacao.getQuantidadeTotal(), consignacao.getValorTotal(), entity.getConfirmada(),
                consignacao.getMostruario() != null ? consignacao.getMostruario().getDescricao() : null, null);
    }

    default PlanejadorRotaConsignacaoResumoDTO toConsignacaoResumoDTO(PlanejadorRotaConsignacaoTrazer entity){
        if (entity == null) return null;
        Consignacao consignacao = entity.getConsignacao();
        return new PlanejadorRotaConsignacaoResumoDTO(consignacao.getId(), consignacao.getDescricao(), consignacao.getQuantidadeTotal(), consignacao.getValorTotal(), entity.getConfirmada(),
                consignacao.getMostruario() != null ? consignacao.getMostruario().getDescricao() : null, null);
    }
}
