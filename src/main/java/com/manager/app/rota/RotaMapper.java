package com.manager.app.rota;

import com.manager.app.consignacao.Consignacao;
import com.manager.app.consignacao.dto.ClienteResumoDTO;
import com.manager.app.endereco.EnderecoMapper;
import com.manager.app.rota.dto.RotaClienteResponseDTO;
import com.manager.app.rota.dto.RotaConsignacaoResumoDTO;
import com.manager.app.rota.dto.RotaResponseDTO;
import com.manager.app.usuario.UsuarioResumoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UsuarioResumoMapper.class, EnderecoMapper.class})
public interface RotaMapper {

    RotaResponseDTO toResponseDTO(Rota entity);

    @Mapping(target = "cliente", expression = "java(clienteResumo(entity))")
    RotaClienteResponseDTO toResponseDTO(RotaCliente entity);

    default ClienteResumoDTO clienteResumo(RotaCliente entity){
        if (entity.getCliente() == null) return null;
        return new ClienteResumoDTO(entity.getCliente().getCodigo(), entity.getCliente().getNome());
    }

    default RotaConsignacaoResumoDTO toConsignacaoResumoDTO(RotaConsignacaoDeixar entity){
        if (entity == null) return null;
        Consignacao consignacao = entity.getConsignacao();
        return new RotaConsignacaoResumoDTO(consignacao.getId(), consignacao.getDescricao(), consignacao.getQuantidadeTotal(), consignacao.getValorTotal(), entity.getConfirmada(),
                consignacao.getMostruario() != null ? consignacao.getMostruario().getDescricao() : null);
    }

    default RotaConsignacaoResumoDTO toConsignacaoResumoDTO(RotaConsignacaoTrazer entity){
        if (entity == null) return null;
        Consignacao consignacao = entity.getConsignacao();
        return new RotaConsignacaoResumoDTO(consignacao.getId(), consignacao.getDescricao(), consignacao.getQuantidadeTotal(), consignacao.getValorTotal(), entity.getConfirmada(),
                consignacao.getMostruario() != null ? consignacao.getMostruario().getDescricao() : null);
    }
}
